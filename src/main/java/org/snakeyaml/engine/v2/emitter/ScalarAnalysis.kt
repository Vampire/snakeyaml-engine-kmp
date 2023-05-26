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
package org.snakeyaml.engine.v2.emitter

/**
 * Accumulate information to choose the scalar style
 *
 * @param scalar - the scalar to be analysed
 * @param empty - `true` for empty scalar
 * @param multiline - `true` if it may take many lines
 * @param allowFlowPlain - `true` if can be plain in flow context
 * @param allowBlockPlain - `true` if can be plain in block context
 * @param allowSingleQuoted - `true` if single quotes are allowed
 * @param allowBlock - `true` when block style is allowed for this scalar
 */
class ScalarAnalysis(
    val scalar: String,
    val empty: Boolean,
    val multiline: Boolean,
    val allowFlowPlain: Boolean,
    val allowBlockPlain: Boolean,
    val allowSingleQuoted: Boolean,
    val allowBlock: Boolean,
)
