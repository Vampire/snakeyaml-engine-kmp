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
 * Represents a map.
 *
 *
 * A map is a collection of unsorted key-value pairs.
 *
 * @param[tag] tag of the node
 * @param[resolved] true when the tag is implicitly resolved
 * @param[value] the entries of this map
 * @param[flowStyle] the flow style of the node
 * @param[startMark] start
 * @param[endMark] end
 */
class MappingNode @JvmOverloads constructor(
  tag: Tag,
  resolved: Boolean = true,
  /**
   * Applications may need to replace the content (Spring Boot).
   * Merging was removed, but it may be implemented.
   */
  override var value: List<NodeTuple>,
  flowStyle: FlowStyle,
  startMark: Optional<Mark> = Optional.empty<Mark>(),
  endMark: Optional<Mark> = Optional.empty<Mark>(),
) : CollectionNode<NodeTuple>(
  tag = tag,
  flowStyle = flowStyle,
  startMark = startMark,
  endMark = endMark,
  resolved = resolved,
) {

  override val nodeType: NodeType
    get() = NodeType.MAPPING

  override fun toString(): String {
    val values = value.joinToString("") { node ->
      val valueNode: Any = when (node.valueNode) {
        is CollectionNode<*> -> System.identityHashCode(node.valueNode) // avoid overflow in case of recursive structures
        else                 -> node
      }
      "{ key=${node.keyNode}; value=$valueNode }"
    }
    return "<${this.javaClass.name} (tag=$tag, values=$values)>"
  }
}