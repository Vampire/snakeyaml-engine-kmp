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
package org.snakeyaml.engine.v2.events

import org.snakeyaml.engine.v2.common.Anchor
import org.snakeyaml.engine.v2.common.FlowStyle
import org.snakeyaml.engine.v2.exceptions.Mark
import java.util.Objects
import java.util.Optional

/**
 * Base class for the start events of the collection nodes.
 */
abstract class CollectionStartEvent(
    anchor: Optional<Anchor>, tag: Optional<String>, implicit: Boolean,
    flowStyle: FlowStyle, startMark: Optional<Mark>, endMark: Optional<Mark>,
) :
    NodeEvent(anchor, startMark, endMark) {
    /**
     * Tag of this collection.
     *
     * @return The tag of this collection, or `empty` if no explicit tag is available.
     */
    val tag: Optional<String>

    /**
     * `true` if the tag can be omitted while this collection is emitted.
     *
     * @return True if the tag can be omitted while this collection is emitted.
     */
    // The implicit flag of a collection start event indicates if the tag may be
    // omitted when the collection is emitted
    val isImplicit: Boolean

    /**
     * `true` if this collection is in flow style, `false` for block style.
     *
     * @return If this collection is in flow style.
     */
    // flag indicates if a collection is block or flow
    val flowStyle: FlowStyle

    init {
        Objects.requireNonNull(tag)
        this.tag = tag
        isImplicit = implicit
        Objects.requireNonNull(flowStyle)
        this.flowStyle = flowStyle
    }

    val isFlow: Boolean
        get() = FlowStyle.FLOW == flowStyle

    override fun toString(): String {
        val builder = StringBuilder()
        anchor.ifPresent { a -> builder.append(" &$a") }
        if (!isImplicit) {
            tag.ifPresent { theTag: String -> builder.append(" <$theTag>") }
        }
        return builder.toString()
    }
}
