/**
 * Copyright (c) 2018, http://www.snakeyaml.org
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.snakeyaml.engine.v1.representer;

import org.snakeyaml.engine.v1.api.RepresentToNode;
import org.snakeyaml.engine.v1.common.FlowStyle;
import org.snakeyaml.engine.v1.common.ScalarStyle;
import org.snakeyaml.engine.v1.nodes.*;

import java.util.*;

/**
 * Represent basic YAML structures: scalar, sequence, mapping
 */
public abstract class BaseRepresenter {
    /**
     * Keep representers which must match the class exactly
     */
    protected final Map<Class<?>, RepresentToNode> representers = new HashMap();
    /**
     * in Java 'null' is not a type. So we have to keep the null representer
     * separately otherwise it will coincide with the default representer which
     * is stored with the key null.
     */
    protected RepresentToNode nullRepresenter;
    // the order is important (map can be also a sequence of key-values)
    /**
     * Keep representers which match a parent of the class to be represented
     */
    protected final Map<Class<?>, RepresentToNode> parentClassRepresenters = new LinkedHashMap();
    protected ScalarStyle defaultScalarStyle = ScalarStyle.PLAIN;
    protected FlowStyle defaultFlowStyle = FlowStyle.AUTO;
    protected final Map<Object, Node> representedObjects = new IdentityHashMap<Object, Node>() {
        public Node put(Object key, Node value) {
            return super.put(key, new AnchorNode(value));
        }
    };

    abstract RepresentToNode getDefaultRepresentFor(Class clazz);

    protected Object objectToRepresent;

    public Node represent(Object data) {
        Node node = representData(data);
        representedObjects.clear();
        objectToRepresent = null;
        return node;
    }

    protected RepresentToNode getRepresenter(Object data) {
        Class<?> clazz = data.getClass();
        // check the same class
        if (representers.containsKey(clazz)) {
            return representers.get(clazz);
        } else {
            // check the parents
            for (Class<?> parentRepresenter : parentClassRepresenters.keySet()) {
                if (parentRepresenter.isInstance(data)) {
                    return parentClassRepresenters.get(parentRepresenter);
                }
            }
            // use defaults
            return getDefaultRepresentFor(clazz);
        }
    }

    protected final Node representData(Object data) {
        objectToRepresent = data;
        // check for identity
        if (representedObjects.containsKey(objectToRepresent)) {
            Node node = representedObjects.get(objectToRepresent);
            return node;
        }
        // check for null first
        if (data == null) {
            Node node = nullRepresenter.representData(null);
            return node;
        }
        RepresentToNode representer = getRepresenter(data);
        return representer.representData(data);
    }

    protected Node representScalar(Tag tag, String value, ScalarStyle style) {
        if (style == ScalarStyle.PLAIN) {
            style = this.defaultScalarStyle;
        }
        Node node = new ScalarNode(tag, value, style);
        return node;
    }

    protected Node representScalar(Tag tag, String value) {
        return representScalar(tag, value, ScalarStyle.PLAIN);
    }

    protected Node representSequence(Tag tag, Iterable<?> sequence, FlowStyle flowStyle) {
        int size = 10;// default for ArrayList
        if (sequence instanceof List<?>) {
            size = ((List<?>) sequence).size();
        }
        List<Node> value = new ArrayList<Node>(size);
        SequenceNode node = new SequenceNode(tag, value, flowStyle);
        representedObjects.put(objectToRepresent, node);
        FlowStyle bestStyle = FlowStyle.FLOW;
        for (Object item : sequence) {
            Node nodeItem = representData(item);
            if (!(nodeItem instanceof ScalarNode && ((ScalarNode) nodeItem).isPlain())) {
                bestStyle = FlowStyle.BLOCK;
            }
            value.add(nodeItem);
        }
        if (flowStyle == FlowStyle.AUTO) {
            if (defaultFlowStyle != FlowStyle.AUTO) {
                node.setFlowStyle(defaultFlowStyle);
            } else {
                node.setFlowStyle(bestStyle);
            }
        }
        return node;
    }

    protected Node representMapping(Tag tag, Map<?, ?> mapping, FlowStyle flowStyle) {
        List<NodeTuple> value = new ArrayList<NodeTuple>(mapping.size());
        MappingNode node = new MappingNode(tag, value, flowStyle);
        representedObjects.put(objectToRepresent, node);
        FlowStyle bestStyle = FlowStyle.FLOW;
        for (Map.Entry<?, ?> entry : mapping.entrySet()) {
            Node nodeKey = representData(entry.getKey());
            Node nodeValue = representData(entry.getValue());
            if (!(nodeKey instanceof ScalarNode && ((ScalarNode) nodeKey).isPlain())) {
                bestStyle = FlowStyle.BLOCK;
            }
            if (!(nodeValue instanceof ScalarNode && ((ScalarNode) nodeValue).isPlain())) {
                bestStyle = FlowStyle.BLOCK;
            }
            value.add(new NodeTuple(nodeKey, nodeValue));
        }
        if (flowStyle == FlowStyle.AUTO) {
            if (defaultFlowStyle != FlowStyle.AUTO) {
                node.setFlowStyle(defaultFlowStyle);
            } else {
                node.setFlowStyle(bestStyle);
            }
        }
        return node;
    }
}
