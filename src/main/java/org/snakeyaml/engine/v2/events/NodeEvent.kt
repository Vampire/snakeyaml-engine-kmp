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
import org.snakeyaml.engine.v2.exceptions.Mark
import java.util.Optional

/**
 * Base class for all events that mark the beginning of a node.
 */
abstract class NodeEvent(
    /**
     * Node anchor by which this node might later be referenced by a [AliasEvent].
     *
     * Note that [AliasEvent]s are by itself [NodeEvent]s and use this property to
     * indicate the referenced anchor.
     *
     * @return Anchor of this node or `null` if no anchor is defined.
     */
    val anchor: Optional<Anchor>,
    startMark: Optional<Mark>,
    endMark: Optional<Mark>,
) : Event(startMark, endMark)