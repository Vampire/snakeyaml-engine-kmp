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
import org.snakeyaml.engine.v2.common.CharConstants
import org.snakeyaml.engine.v2.common.ScalarStyle
import org.snakeyaml.engine.v2.exceptions.Mark
import java.util.*
import java.util.stream.Collectors

/**
 * Marks a scalar value.
 */
class ScalarEvent @JvmOverloads constructor(
    anchor: Optional<Anchor>,

    /**
     * Tag of this scalar.
     *
     * @return The tag of this scalar, or `null` if no explicit tag is available.
     */
    val tag: Optional<String>,
    // The implicit flag of a scalar event is a pair of boolean values that
    // indicate if the tag may be omitted when the scalar is emitted in a plain
    // and non-plain style correspondingly.
    val implicit: ImplicitTuple,
    /**
     * String representation of the value, without quotes and escaping.
     *
     * @return Value as Unicode string.
     */
    val value: String,
    /**
     * Style of the scalar.
     *
     * * `null` - Flow Style - Plain
     * * `'\''` - Flow Style - Single-Quoted
     * * `"` - Flow Style - Double-Quoted
     * * `|` - Block Style - Literal
     * * `&gt;` - Block Style - Folded
     *
     * @return Style of the scalar.
     */
    // style flag of a scalar event indicates the style of the scalar. Possible
    // values are None, '', '\'', '"', '|', '>'
    val scalarStyle: ScalarStyle,
    startMark: Optional<Mark> = Optional.empty(),
    endMark: Optional<Mark> = Optional.empty(),
) : NodeEvent(anchor, startMark, endMark) {

    override val eventId: ID
        get() = ID.Scalar

    val isPlain: Boolean
        get() = scalarStyle == ScalarStyle.PLAIN

    override fun toString(): String {
        return buildString {
            append("=VAL")
            anchor.ifPresent { a -> append(" &$a") }
            if (implicit.bothFalse()) {
                tag.ifPresent { theTag: String -> append(" <$theTag>") }
            }
            append(" ")
            append(scalarStyle.toString())
            append(escapedValue())
        }
    }

    fun escapedValue(): String {
        return value
            .codePoints()
            .filter { i: Int -> i < Character.MAX_VALUE.code }
            .mapToObj { ch: Int ->
                CharConstants.escapeChar(String(Character.toChars(ch)))
            }
            .collect(Collectors.joining(""))
    }
}