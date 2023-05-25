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
package org.snakeyaml.engine.v2.parser

import org.snakeyaml.engine.v2.common.SpecVersion
import java.util.*

/**
 * Store the internal state for directives
 */
internal data class VersionTagsTuple(
    val specVersion: Optional<SpecVersion>,
    val tags: Map<String, String>,
) {

    @Deprecated("help during java->kt auto convert", ReplaceWith("specVersion"))
    @JvmName("getSpecVersionJvm")
    fun getSpecVersion() = specVersion

    @Deprecated("help during java->kt auto convert", ReplaceWith("tags"))
    @JvmName("getTagsJvm")
    fun getTags() = tags

    override fun toString(): String = "VersionTagsTuple<$specVersion, $tags>"
}
