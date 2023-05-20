/*
 * Copyright (c) 2018, SnakeYAML
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.snakeyaml.engine.v2.nodes

import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.exceptions.Mark
import java.util.*

/**
 * Represents a sequence.
 *
 * A sequence is an ordered collection of nodes.
 *
 * @param[value] the [Node]s in this sequence, in the specified order
 */
class SequenceNode @JvmOverloads constructor(
    tag: Tag,
    resolved: Boolean = true,
    override val value: List<Node>,
    flowStyle: FlowStyle,
    startMark: Optional<Mark> = Optional.empty<Mark>(),
    endMark: Optional<Mark> = Optional.empty<Mark>(),
) : CollectionNode<Node>(
    tag,
    flowStyle,
    startMark,
    endMark,
    resolved = resolved,
) {
    override val nodeType: NodeType
        get() = NodeType.SEQUENCE

    override fun toString(): String {
        val values = value.joinToString(",") { node ->
            when (node) {
                // avoid overflow in case of recursive structures
                is CollectionNode<*> -> System.identityHashCode(node).toString()

                else                 -> node.toString()
            }
        }

        return "<${this.javaClass.name} (tag=$tag, value=[$values])>"
    }
}
