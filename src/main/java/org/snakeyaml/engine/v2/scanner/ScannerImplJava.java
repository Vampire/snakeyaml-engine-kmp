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
package org.snakeyaml.engine.v2.scanner;

import kotlin.NotImplementedError;
import org.jetbrains.annotations.NotNull;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.comments.CommentType;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.CharConstants;
import org.snakeyaml.engine.v2.common.ScalarStyle;
import org.snakeyaml.engine.v2.common.UriEncoder;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.ScannerException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.scanner.Chomping.Indicator;
import org.snakeyaml.engine.v2.tokens.*;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.*;
import java.util.regex.Pattern;

import static org.snakeyaml.engine.v2.common.CharConstants.ESCAPE_CODES;
import static org.snakeyaml.engine.v2.common.CharConstants.ESCAPE_REPLACEMENTS;

final class ScannerImplJava implements Scanner {

  static final String DIRECTIVE_PREFIX = "while scanning a directive";
  static final String EXPECTED_ALPHA_ERROR_PREFIX =
      "expected alphabetic or numeric character, but found ";
  static final String SCANNING_SCALAR = "while scanning a block scalar";
  static final String SCANNING_PREFIX = "while scanning a ";
  /**
   * A regular expression matching characters which are not in the hexadecimal set (0-9, A-F, a-f).
   */
  static final Pattern NOT_HEXA = Pattern.compile("[^0-9A-Fa-f]");

  final StreamReader reader;
  /**
   * List of processed tokens that are not yet emitted.
   */
  final List<Token> tokens;
  /**
   * Past indentation levels.
   */
  final kotlin.collections.ArrayDeque<Integer> indents;
  /**
   * Keep track of possible simple keys. This is a dictionary. The key is `flow_level`; there can be
   * no more than one possible simple key for each level. The value is a SimpleKey record:
   * (token_number, required, index, line, column, mark) A simple key may start with ALIAS, ANCHOR,
   * TAG, SCALAR(flow), '[', or '{' tokens.
   */
  final Map<Integer, SimpleKey> possibleSimpleKeys;
  final LoadSettings settings;
  /**
   * Had we reached the end of the stream?
   */
  boolean done = false;
  /**
   * The number of unclosed '{' and '['. `isBlockContext()` means block context.
   */
  int flowLevel = 0;
  /**
   * The last added token
   */
  Token lastToken;

  /**
   * Variables related to simple keys treatment.
   * Number of tokens that were emitted through the `get_token` method.
   */
  int tokensTaken = 0;
  /**
   * The current indentation level.
   */
  int indent = -1;
  /**
   * <pre>
   * A simple key is a key that is not denoted by the '?' indicator.
   * Example of simple keys:
   *   ---
   *   block simple key: value
   *   ? not a simple key:
   *   : { flow simple key: value }
   * We emit the KEY token before all keys, so when we find a potential
   * simple key, we try to locate the corresponding ':' indicator.
   * Simple keys should be limited to a single line and 1024 characters.
   *
   * Can a simple key start at the current position? A simple key may
   * start:
   * - at the beginning of the line, not counting indentation spaces
   *       (in block context),
   * - after '{', '[', ',' (in the flow context),
   * - after '?', ':', '-' (in the block context).
   * In the block context, this flag also signifies if a block collection
   * may start at the current position.
   * </pre>
   */
  boolean allowSimpleKey = true;

  /**
   * Create
   *
   * @param settings - configurable options
   * @param reader   - the input
   */
  public ScannerImplJava(LoadSettings settings, StreamReader reader) {
    this.reader = reader;
    this.settings = settings;
    this.tokens = new ArrayList<>(100);
    this.indents = new kotlin.collections.ArrayDeque<>(10);
    // The order in possibleSimpleKeys is kept for nextPossibleSimpleKey()
    this.possibleSimpleKeys = new LinkedHashMap<>();
    //fetchStreamStart();// Add the STREAM-START token.
  }

  public boolean checkToken(@NotNull Token.ID... choices) {
    throw new NotImplementedError("converted to Kotlin");
  }

  @NotNull
  public Token peekToken() {
    throw new NotImplementedError("converted to Kotlin");
  }

  @Override
  public boolean hasNext() {
    throw new NotImplementedError("converted to Kotlin");
  }

  @NotNull
  public Token next() {
    throw new NotImplementedError("converted to Kotlin");
  }


  //region Private methods.

  void addToken(Token token) {
    lastToken = token;
    this.tokens.add(token);
  }


  boolean isBlockContext() {
    return this.flowLevel == 0;
  }

  boolean isFlowContext() {
    return !isBlockContext();
  }

  //region Simple keys treatment.



  /**
   * Remove the saved possible key position at the current flow level.
   */
  void removePossibleSimpleKey() {
    SimpleKey key = possibleSimpleKeys.remove(flowLevel);
    if (key != null && key.isRequired()) {
      throw new ScannerException("while scanning a simple key", key.getMark(),
          "could not find expected ':'", reader.getMark());
    }
  }



  CommentToken scanComment(CommentType type) {
    // See the specification for details.
    Optional<Mark> startMark = reader.getMark();
    reader.forward();
    int length = 0;
    while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(length))) {
      length++;
    }
    String value = reader.prefixForward(length);
    Optional<Mark> endMark = reader.getMark();
    return new CommentToken(type, value, startMark, endMark);
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  List<Token> scanDirective() {
    // See the specification for details.
    Optional<Mark> startMark = reader.getMark();
    Optional<Mark> endMark;
    reader.forward();
    String name = scanDirectiveName(startMark);
    Optional<List<?>> value;
    if (DirectiveToken.YAML_DIRECTIVE.equals(name)) {
      value = Optional.of(scanYamlDirectiveValue(startMark));
      endMark = reader.getMark();
    } else if (DirectiveToken.TAG_DIRECTIVE.equals(name)) {
      value = Optional.of(scanTagDirectiveValue(startMark));
      endMark = reader.getMark();
    } else {
      endMark = reader.getMark();
      int ff = 0;
      while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(ff))) {
        ff++;
      }
      if (ff > 0) {
        reader.forward(ff);
      }
      value = Optional.empty();
    }
    CommentToken commentToken = scanDirectiveIgnoredLine(startMark);
    DirectiveToken token = new DirectiveToken(name, value, startMark, endMark);
    return makeTokenList(token, commentToken);
  }

  /**
   * Scan a directive name. Directive names are a series of non-space characters.
   */
  String scanDirectiveName(Optional<Mark> startMark) {
    // See the specification for details.
    int length = 0;
    // A Directive-name is a sequence of alphanumeric characters
    // (a-z,A-Z,0-9). We scan until we find something that isn't.
    // This disagrees with the specification.
    int c = reader.peek(length);
    while (CharConstants.ALPHA.has(c)) {
      length++;
      c = reader.peek(length);
    }
    // If the peeked name is empty, an error occurs.
    if (length == 0) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          EXPECTED_ALPHA_ERROR_PREFIX + s + "(" + c + ")", reader.getMark());
    }
    String value = reader.prefixForward(length);
    c = reader.peek();
    if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          EXPECTED_ALPHA_ERROR_PREFIX + s + "(" + c + ")", reader.getMark());
    }
    return value;
  }

  List<Integer> scanYamlDirectiveValue(Optional<Mark> startMark) {
    // See the specification for details.
    while (reader.peek() == ' ') {
      reader.forward();
    }
    Integer major = scanYamlDirectiveNumber(startMark);
    int c = reader.peek();
    if (c != '.') {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected a digit or '.', but found " + s + "(" + c + ")", reader.getMark());
    }
    reader.forward();
    Integer minor = scanYamlDirectiveNumber(startMark);
    c = reader.peek();
    if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected a digit or ' ', but found " + s + "(" + c + ")", reader.getMark());
    }
    List<Integer> result = new ArrayList<>(2);
    result.add(major);
    result.add(minor);
    return result;
  }

  /**
   * Read a %YAML directive number: this is either the major or the minor part. Stop reading at a
   * non-digit character (usually either '.' or '\n').
   */
  Integer scanYamlDirectiveNumber(Optional<Mark> startMark) {
    // See the specification for details.
    int c = reader.peek();
    if (!Character.isDigit(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected a digit, but found " + s + "(" + (c) + ")", reader.getMark());
    }
    int length = 0;
    while (Character.isDigit(reader.peek(length))) {
      length++;
    }
    String number = reader.prefixForward(length);
    if (length > 3) {
      throw new ScannerException("while scanning a YAML directive", startMark,
          "found a number which cannot represent a valid version: " + number, reader.getMark());
    }
    return Integer.parseInt(number);
  }

  /**
   * <p>
   * Read a %TAG directive value:
   * <p>
   *
   * <pre>
   * s-ignored-space+ c-tag-handle s-ignored-space+ ns-tag-prefix s-l-comments
   * </pre>
   * <p>
   * </p>
   */
  List<String> scanTagDirectiveValue(Optional<Mark> startMark) {
    // See the specification for details.
    while (reader.peek() == ' ') {
      reader.forward();
    }
    String handle = scanTagDirectiveHandle(startMark);
    while (reader.peek() == ' ') {
      reader.forward();
    }
    String prefix = scanTagDirectivePrefix(startMark);
    List<String> result = new ArrayList<>(2);
    result.add(handle);
    result.add(prefix);
    return result;
  }

  /**
   * Scan a %TAG directive's handle. This is YAML's c-tag-handle.
   *
   * @param startMark - start
   * @return the directive value
   */
  String scanTagDirectiveHandle(Optional<Mark> startMark) {
    // See the specification for details.
    String value = scanTagHandle("directive", startMark);
    int c = reader.peek();
    if (c != ' ') {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected ' ', but found " + s + "(" + c + ")", reader.getMark());
    }
    return value;
  }

  /**
   * Scan a %TAG directive's prefix. This is YAML's ns-tag-prefix.
   */
  String scanTagDirectivePrefix(Optional<Mark> startMark) {
    // See the specification for details.
    String value = scanTagUri("directive", CharConstants.URI_CHARS_FOR_TAG_PREFIX, startMark);
    int c = reader.peek();
    if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected ' ', but found " + s + "(" + c + ")", reader.getMark());
    }
    return value;
  }

  CommentToken scanDirectiveIgnoredLine(Optional<Mark> startMark) {
    // See the specification for details.
    while (reader.peek() == ' ') {
      reader.forward();
    }
    CommentToken commentToken = null;
    if (reader.peek() == '#') {
      CommentToken comment = scanComment(CommentType.IN_LINE);
      if (settings.parseComments) {
        commentToken = comment;
      }
    }
    int c = reader.peek();
    if (!scanLineBreak().isPresent() && c != 0) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(DIRECTIVE_PREFIX, startMark,
          "expected a comment or a line break, but found " + s + "(" + c + ")", reader.getMark());
    }
    return commentToken;
  }

  /**
   * <pre>
   * The YAML 1.2 specification does not restrict characters for anchors and
   * aliases. This may lead to problems.
   * see <a href=
   * "https://bitbucket.org/snakeyaml/snakeyaml/issues/485/alias-names-are-too-permissive-compared-to">issue 485</a>
   * This implementation tries to follow <a href=
   * "https://github.com/yaml/yaml-spec/blob/master/rfc/RFC-0003.md">RFC-0003</a>
   * </pre>
   */
  Token scanAnchor(boolean isAnchor) {
    Optional<Mark> startMark = reader.getMark();
    int indicator = reader.peek();
    String name = indicator == '*' ? "alias" : "anchor";
    reader.forward();
    int length = 0;
    int c = reader.peek(length);
    // Anchor may not contain ",[]{}"
    while (CharConstants.NULL_BL_T_LINEBR.hasNo(c, ",[]{}/.*&")) {
      length++;
      c = reader.peek(length);
    }
    if (length == 0) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException("while scanning an " + name, startMark,
          "unexpected character found " + s + "(" + c + ")", reader.getMark());
    }
    String value = reader.prefixForward(length);
    c = reader.peek();
    if (CharConstants.NULL_BL_T_LINEBR.hasNo(c, "?:,]}%@`")) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException("while scanning an " + name, startMark,
          "unexpected character found " + s + "(" + c + ")", reader.getMark());
    }
    Optional<Mark> endMark = reader.getMark();
    Token tok;
    if (isAnchor) {
      tok = new AnchorToken(new Anchor(value), startMark, endMark);
    } else {
      tok = new AliasToken(new Anchor(value), startMark, endMark);
    }
    return tok;
  }

  /**
   * Scan a Tag property. A Tag property may be specified in one of three ways: c-verbatim-tag,
   * c-ns-shorthand-tag, or c-ns-non-specific-tag
   * <p>
   * c-verbatim-tag takes the form !<ns-uri-char+> and must be delivered verbatim (as-is) to the
   * application. In particular, verbatim tags are not subject to tag resolution.
   * <p>
   * c-ns-shorthand-tag is a valid tag handle followed by a non-empty suffix. If the tag handle is a
   * c-primary-tag-handle ('!') then the suffix must have all exclamation marks properly URI-escaped
   * (%21); otherwise, the string will look like a named tag handle: !foo!bar would be interpreted
   * as (handle="!foo!", suffix="bar").
   * <p>
   * c-ns-non-specific-tag is always a lone '!'; this is only useful for plain scalars, where its
   * specification means that the scalar MUST be resolved to have type tag:yaml.org,2002:str.
   * <p>
   * TODO Note that this method does not enforce rules about local versus global tags!
   */
  Token scanTag() {
    // See the specification for details.
    Optional<Mark> startMark = reader.getMark();
    // Determine the type of tag property based on the first character
    // encountered
    int c = reader.peek(1);
    final String handle;
    final String suffix;
    // Verbatim tag! (c-verbatim-tag)
    if (c == '<') {
      // Skip the exclamation mark and &gt;, then read the tag suffix (as a URI).
      reader.forward(2);
      suffix = scanTagUri("tag", CharConstants.URI_CHARS_FOR_TAG_PREFIX, startMark);
      c = reader.peek();
      if (c != '>') {
        // If there are any characters between the end of the tag-suffix
        // URI and the closing &gt;, then an error has occurred.
        final String s = String.valueOf(Character.toChars(c));
        throw new ScannerException("while scanning a tag", startMark,
            "expected '>', but found '" + s + "' (" + c + ")", reader.getMark());
      }
      handle = null;
      reader.forward();
    } else if (CharConstants.NULL_BL_T_LINEBR.has(c)) {
      // A NUL, blank, tab, or line-break means that this was a
      // c-ns-non-specific tag.
      suffix = "!";
      handle = null;
      reader.forward();
    } else {
      // Any other character implies c-ns-shorthand-tag type.

      // Look ahead in the stream to determine whether this tag property
      // is of the form !foo or !foo!bar.
      int length = 1;
      boolean useHandle = false;
      while (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
        if (c == '!') {
          useHandle = true;
          break;
        }
        length++;
        c = reader.peek(length);
      }
      // If we need to use a handle, scan it in; otherwise, the handle is
      // presumed to be '!'.
      if (useHandle) {
        handle = scanTagHandle("tag", startMark);
      } else {
        handle = "!";
        reader.forward();
      }
      suffix = scanTagUri("tag", CharConstants.URI_CHARS_FOR_TAG_SUFFIX, startMark);
    }
    c = reader.peek();
    // Check that the next character is allowed to follow a tag-property, if it is not, raise the
    // error.
    if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException("while scanning a tag", startMark,
          "expected ' ', but found '" + s + "' (" + (c) + ")", reader.getMark());
    }
    TagTuple value = new TagTuple(Optional.ofNullable(handle), suffix);
    Optional<Mark> endMark = reader.getMark();
    return new TagToken(value, startMark, endMark);
  }

  /**
   * Scan literal and folded scalar
   *
   * @param style - either literal or folded style
   */
  List<Token> scanBlockScalar(ScalarStyle style) {
    // See the specification for details.
    StringBuilder stringBuilder = new StringBuilder();
    Optional<Mark> startMark = reader.getMark();
    // Scan the header.
    reader.forward();
    Chomping chomping = scanBlockScalarIndicators(startMark);
    CommentToken commentToken = scanBlockScalarIgnoredLine(startMark);

    // Determine the indentation level and go to the first non-empty line.
    int minIndent = this.indent + 1;
    if (minIndent < 1) {
      minIndent = 1;
    }
    String breaks;
    int maxIndent;
    int blockIndent;
    Optional<Mark> endMark;
    if (chomping.increment.isPresent()) {
      // increment is explicit
      blockIndent = minIndent + chomping.increment.get() - 1;
      BreakIntentHolder brme = scanBlockScalarBreaks(blockIndent);
      breaks = brme.breaks;
      endMark = brme.endMark;
    } else {
      // increment (block indent) must be detected in the first non-empty line.
      BreakIntentHolder brme = scanBlockScalarIndentation();
      breaks = brme.breaks;
      maxIndent = brme.maxIndent;
      endMark = brme.endMark;
      blockIndent = Math.max(minIndent, maxIndent);
    }

    Optional<String> lineBreakOpt = Optional.empty();
    // Scan the inner part of the block scalar.
    if (this.reader.getColumn() < blockIndent && this.indent != reader.getColumn()) {
      // it means that there is indent, but less than expected
      // fix S98Z - Block scalar with more spaces than first content line
      throw new ScannerException("while scanning a block scalar", startMark,
          " the leading empty lines contain more spaces (" + blockIndent
              + ") than the first non-empty line.",
          reader.getMark());
    }
    while (this.reader.getColumn() == blockIndent && reader.peek() != 0) {
      stringBuilder.append(breaks);
      boolean leadingNonSpace = " \t".indexOf(reader.peek()) == -1;
      int length = 0;
      while (CharConstants.NULL_OR_LINEBR.hasNo(reader.peek(length))) {
        length++;
      }
      stringBuilder.append(reader.prefixForward(length));
      lineBreakOpt = scanLineBreak();
      BreakIntentHolder brme = scanBlockScalarBreaks(blockIndent);
      breaks = brme.breaks;
      endMark = brme.endMark;
      if (this.reader.getColumn() == blockIndent && reader.peek() != 0) {

        // Unfortunately, folding rules are ambiguous.
        //
        // This is the folding according to the specification:
        if (style == ScalarStyle.FOLDED && "\n".equals(lineBreakOpt.orElse("")) && leadingNonSpace
            && " \t".indexOf(reader.peek()) == -1) {
          if (breaks.length() == 0) {
            stringBuilder.append(" ");
          }
        } else {
          stringBuilder.append(lineBreakOpt.orElse(""));
        }
      } else {
        break;
      }
    }
    // Chomp the tail.
    if (chomping.value == Indicator.CLIP || chomping.value == Indicator.KEEP) {
      // add the final line break (if exists !) TODO find out if to add anyway
      stringBuilder.append(lineBreakOpt.orElse(""));
    }
    if (chomping.value == Indicator.KEEP) {
      // any trailing empty lines are considered to be part of the scalar’s content
      stringBuilder.append(breaks);
    }
    // We are done.
    ScalarToken scalarToken =
        new ScalarToken(stringBuilder.toString(), false, startMark, endMark, style);
    return makeTokenList(commentToken, scalarToken);
  }

  /**
   * Scan a block scalar indicator. The block scalar indicator includes two optional components,
   * which may appear in either order.
   * <p>
   * A block indentation indicator is a non-zero digit describing the indentation level of the block
   * scalar to follow. This indentation is an additional number of spaces relative to the current
   * indentation level.
   * <p>
   * A block chomping indicator is a + or -, selecting the chomping mode away from the default
   * (clip) to either -(strip) or +(keep).
   */
  Chomping scanBlockScalarIndicators(Optional<Mark> startMark) {
    // See the specification for details.
    int indicator = Integer.MIN_VALUE;
    Optional<Integer> increment = Optional.empty();
    int c = reader.peek();
    if (c == '-' || c == '+') {
      indicator = c;
      reader.forward();
      c = reader.peek();
      if (Character.isDigit(c)) {
        int incr = Integer.parseInt(String.valueOf(Character.toChars(c)));
        if (incr == 0) {
          throw new ScannerException(SCANNING_SCALAR, startMark,
              "expected indentation indicator in the range 1-9, but found 0", reader.getMark());
        }
        increment = Optional.of(incr);
        reader.forward();
      }
    } else if (Character.isDigit(c)) {
      int incr = Integer.parseInt(String.valueOf(Character.toChars(c)));
      if (incr == 0) {
        throw new ScannerException(SCANNING_SCALAR, startMark,
            "expected indentation indicator in the range 1-9, but found 0", reader.getMark());
      }
      increment = Optional.of(incr);
      reader.forward();
      c = reader.peek();
      if (c == '-' || c == '+') {
        indicator = c;
        reader.forward();
      }
    }
    c = reader.peek();
    if (CharConstants.NULL_BL_LINEBR.hasNo(c)) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(SCANNING_SCALAR, startMark,
          "expected chomping or indentation indicators, but found " + s + "(" + c + ")",
          reader.getMark());
    }
    return new Chomping(indicator, increment);
  }

  /**
   * Scan to the end of the line after a block scalar has been scanned; the only things that are
   * permitted at this time are comments and spaces.
   */
  CommentToken scanBlockScalarIgnoredLine(Optional<Mark> startMark) {
    // See the specification for details.

    // Forward past any number of trailing spaces
    while (reader.peek() == ' ') {
      reader.forward();
    }

    // If a comment occurs, scan to just before the end of line.
    CommentToken commentToken = null;
    if (reader.peek() == '#') {
      commentToken = scanComment(CommentType.IN_LINE);
    }
    // If the next character is not a null or line break, an error has
    // occurred.
    int c = reader.peek();
    if (!scanLineBreak().isPresent() && c != 0) {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(SCANNING_SCALAR, startMark,
          "expected a comment or a line break, but found " + s + "(" + c + ")", reader.getMark());
    }
    return commentToken;
  }

  /**
   * Scans for the indentation of a block scalar implicitly. This mechanism is used only if the
   * block did not explicitly state an indentation to be used.
   */
  BreakIntentHolder scanBlockScalarIndentation() {
    // See the specification for details.
    StringBuilder chunks = new StringBuilder();
    int maxIndent = 0;
    Optional<Mark> endMark = reader.getMark();
    // Look ahead some number of lines until the first non-blank character
    // occurs; the determined indentation will be the maximum number of
    // leading spaces on any of these lines.
    while (CharConstants.LINEBR.has(reader.peek(), " \r")) {
      if (reader.peek() != ' ') {
        // If the character isn't a space, it must be some kind of
        // line-break; scan the line break and track it.
        chunks.append(scanLineBreak().orElse(""));
        endMark = reader.getMark();
      } else {
        // If the character is a space, move forward to the next
        // character; if we surpass our previous maximum for indent
        // level, update that too.
        reader.forward();
        if (this.reader.getColumn() > maxIndent) {
          maxIndent = reader.getColumn();
        }
      }
    }
    // Pass several results back together (Java 8 does not have records)
    return new BreakIntentHolder(chunks.toString(), maxIndent, endMark);
  }

  BreakIntentHolder scanBlockScalarBreaks(int indent) {
    // See the specification for details.
    StringBuilder chunks = new StringBuilder();
    Optional<Mark> endMark = reader.getMark();
    int col = this.reader.getColumn();
    // Scan for up to the expected indentation-level of spaces, then move
    // forward past that amount.
    while (col < indent && reader.peek() == ' ') {
      reader.forward();
      col++;
    }

    // Consume one or more line breaks followed by any amount of spaces,
    // until we find something that isn't a line-break.
    Optional<String> lineBreakOpt;
    while ((lineBreakOpt = scanLineBreak()).isPresent()) {
      chunks.append(lineBreakOpt.get());
      endMark = reader.getMark();
      // Scan past up to (indent) spaces on the next line, then forward
      // past them.
      col = this.reader.getColumn();
      while (col < indent && reader.peek() == ' ') {
        reader.forward();
        col++;
      }
    }
    // Return both the assembled intervening string and the end-mark.
    return new BreakIntentHolder(chunks.toString(), -1, endMark);
  }

  /**
   * Scan a flow-style scalar. Flow scalars are presented in one of two forms; first, a flow scalar
   * may be a double-quoted string; second, a flow scalar may be a single-quoted string.
   *
   * <pre>
   * See the specification for details.
   * Note that we loose indentation rules for quoted scalars. Quoted
   * scalars don't need to adhere indentation because &quot; and ' clearly
   * mark the beginning and the end of them. Therefore we are less
   * restrictive then the specification requires. We only need to check
   * that document separators are not included in scalars.
   * </pre>
   */
  Token scanFlowScalar(final ScalarStyle style) {
    // The style will be either single- or double-quoted; we determine this
    // by the first character in the entry (supplied)
    final boolean doubleValue = style == ScalarStyle.DOUBLE_QUOTED;
    StringBuilder chunks = new StringBuilder();
    Optional<Mark> startMark = reader.getMark();
    int quote = reader.peek();
    reader.forward();
    chunks.append(scanFlowScalarNonSpaces(doubleValue, startMark));
    while (reader.peek() != quote) {
      chunks.append(scanFlowScalarSpaces(startMark));
      chunks.append(scanFlowScalarNonSpaces(doubleValue, startMark));
    }
    reader.forward();
    Optional<Mark> endMark = reader.getMark();
    return new ScalarToken(chunks.toString(), false, startMark, endMark, style);
  }

  /**
   * Scan some number of flow-scalar non-space characters.
   */
  String scanFlowScalarNonSpaces(boolean doubleQuoted, Optional<Mark> startMark) {
    // See the specification for details.
    StringBuilder chunks = new StringBuilder();
    while (true) {
      // Scan through any number of characters which are not: NUL, blank,
      // tabs, line breaks, single-quotes, double-quotes, or backslashes.
      int length = 0;
      while (CharConstants.NULL_BL_T_LINEBR.hasNo(reader.peek(length), "'\"\\")) {
        length++;
      }
      if (length != 0) {
        chunks.append(reader.prefixForward(length));
      }
      // Depending on our quoting-type, the characters ', " and \ have
      // differing meanings.
      int c = reader.peek();
      if (!doubleQuoted && c == '\'' && reader.peek(1) == '\'') {
        chunks.append("'");
        reader.forward(2);
      } else if ((doubleQuoted && c == '\'') || (!doubleQuoted && "\"\\".indexOf(c) != -1)) {
        chunks.appendCodePoint(c);
        reader.forward();
      } else if (doubleQuoted && c == '\\') {
        reader.forward();
        c = reader.peek();
        if (!Character.isSupplementaryCodePoint(c) && ESCAPE_REPLACEMENTS.containsKey((char) c)) {
          // The character is one of the single-replacement
          // types; these are replaced with a literal character
          // from the mapping.
          chunks.append(ESCAPE_REPLACEMENTS.get((char) c));
          reader.forward();
        } else if (!Character.isSupplementaryCodePoint(c) && ESCAPE_CODES.containsKey((char) c)) {
          // The character is a multi-digit escape sequence, with
          // length defined by the value in the ESCAPE_CODES map.
          length = ESCAPE_CODES.get((char) c);
          reader.forward();
          String hex = reader.prefix(length);
          if (NOT_HEXA.matcher(hex).find()) {
            throw new ScannerException("while scanning a double-quoted scalar", startMark,
                "expected escape sequence of " + length + " hexadecimal numbers, but found: " + hex,
                reader.getMark());
          }
          int decimal = Integer.parseInt(hex, 16);
          try {
            String unicode = new String(Character.toChars(decimal));
            chunks.append(unicode);
            reader.forward(length);
          } catch (IllegalArgumentException e) {
            throw new ScannerException("while scanning a double-quoted scalar", startMark,
                "found unknown escape character " + hex, reader.getMark());
          }
        } else if (scanLineBreak().isPresent()) {
          chunks.append(scanFlowScalarBreaks(startMark));
        } else {
          final String s = String.valueOf(Character.toChars(c));
          throw new ScannerException("while scanning a double-quoted scalar", startMark,
              "found unknown escape character " + s + "(" + c + ")", reader.getMark());
        }
      } else {
        return chunks.toString();
      }
    }
  }

  String scanFlowScalarSpaces(Optional<Mark> startMark) {
    // See the specification for details.
    StringBuilder chunks = new StringBuilder();
    int length = 0;
    // Scan through any number of whitespace (space, tab) characters,
    // consuming them.
    while (" \t".indexOf(reader.peek(length)) != -1) {
      length++;
    }
    String whitespaces = reader.prefixForward(length);
    int c = reader.peek();
    if (c == 0) {
      // A flow scalar cannot end with an end-of-stream
      throw new ScannerException("while scanning a quoted scalar", startMark,
          "found unexpected end of stream", reader.getMark());
    }
    // If we encounter a line break, scan it into our assembled string...
    Optional<String> lineBreakOpt = scanLineBreak();
    if (lineBreakOpt.isPresent()) {
      String breaks = scanFlowScalarBreaks(startMark);
      if (!"\n".equals(lineBreakOpt.get())) {
        chunks.append(lineBreakOpt.get());
      } else if (breaks.length() == 0) {
        chunks.append(" ");
      }
      chunks.append(breaks);
    } else {
      chunks.append(whitespaces);
    }
    return chunks.toString();
  }

  String scanFlowScalarBreaks(Optional<Mark> startMark) {
    // See the specification for details.
    StringBuilder chunks = new StringBuilder();
    while (true) {
      // Instead of checking indentation, we check for document
      // separators.
      String prefix = reader.prefix(3);
      if (("---".equals(prefix) || "...".equals(prefix))
          && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
        throw new ScannerException("while scanning a quoted scalar", startMark,
            "found unexpected document separator", reader.getMark());
      }
      // Scan past any number of spaces and tabs, ignoring them
      while (" \t".indexOf(reader.peek()) != -1) {
        reader.forward();
      }
      // If we stopped at a line break, add that; otherwise, return the
      // assembled set of scalar breaks.
      Optional<String> lineBreakOpt = scanLineBreak();
      if (lineBreakOpt.isPresent()) {
        chunks.append(lineBreakOpt.get());
      } else {
        return chunks.toString();
      }
    }
  }


  /**
   * Helper for scanPlainSpaces method when comments are enabled.
   * The ensures that blank lines and comments following a multi-line plain token are not swallowed
   * up
   */
  boolean atEndOfPlain() {
    // peak ahead to find end of whitespaces and the column at which it occurs
    int wsLength = 0;
    int wsColumn = this.reader.getColumn();
    {
      int c;
      while ((c = reader.peek(wsLength)) != 0 && CharConstants.NULL_BL_T_LINEBR.has(c)) {
        wsLength++;
        if (!CharConstants.LINEBR.has(c) && (c != '\r' || reader.peek(wsLength + 1) != '\n')
            && c != 0xFEFF) {
          wsColumn++;
        } else {
          wsColumn = 0;
        }
      }
    }

    // if we see, a comment or end of string or change decrease in indent, we are done
    // Do not chomp end of lines and blanks, they will be handled by the main loop.
    if (reader.peek(wsLength) == '#' || reader.peek(wsLength + 1) == 0
        || isBlockContext() && wsColumn < this.indent) {
      return true;
    }

    // if we see, after the space, a key-value followed by a ':', we are done
    // Do not chomp end of lines and blanks, they will be handled by the main loop.
    if (isBlockContext()) {
      int c;
      for (int extra = 1; (c = reader.peek(wsLength + extra)) != 0
          && !CharConstants.NULL_BL_T_LINEBR.has(c); extra++) {
        if (c == ':' && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(wsLength + extra + 1))) {
          return true;
        }
      }
    }

    // None of the above so safe to chomp the spaces.
    return false;
  }

  /**
   * See the specification for details. SnakeYAML and libyaml allow tabs inside plain scalar
   */
  String scanPlainSpaces() {
    int length = 0;
    while (reader.peek(length) == ' ' || reader.peek(length) == '\t') {
      length++;
    }
    String whitespaces = reader.prefixForward(length);
    Optional<String> lineBreakOpt = scanLineBreak();
    if (lineBreakOpt.isPresent()) {
      this.allowSimpleKey = true;
      String prefix = reader.prefix(3);
      if ("---".equals(prefix)
          || "...".equals(prefix) && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
        return "";
      }
      if (settings.parseComments && atEndOfPlain()) {
        return "";
      }
      StringBuilder breaks = new StringBuilder();
      while (true) {
        if (reader.peek() == ' ') {
          reader.forward();
        } else {
          Optional<String> lbOpt = scanLineBreak();
          if (lbOpt.isPresent()) {
            breaks.append(lbOpt.get());
            prefix = reader.prefix(3);
            if ("---".equals(prefix)
                || "...".equals(prefix) && CharConstants.NULL_BL_T_LINEBR.has(reader.peek(3))) {
              return "";
            }
          } else {
            break;
          }
        }
      }
      if (!"\n".equals(lineBreakOpt.orElse(""))) {
        return lineBreakOpt.orElse("") + breaks;
      } else if (breaks.length() == 0) {
        return " ";
      }
      return breaks.toString();
    }
    return whitespaces;
  }

  /**
   * <p>
   * Scan a Tag handle. A Tag handle takes one of three forms:
   * <p>
   *
   * <pre>
   * "!" (c-primary-tag-handle)
   * "!!" (ns-secondary-tag-handle)
   * "!(name)!" (c-named-tag-handle)
   * </pre>
   * <p>
   * Where (name) must be formatted as an ns-word-char.
   * </p>
   *
   *
   * <pre>
   * See the specification for details.
   * For some strange reasons, the specification does not allow '_' in
   * tag handles. I have allowed it anyway.
   * </pre>
   */
  String scanTagHandle(String name, Optional<Mark> startMark) {
    int c = reader.peek();
    if (c != '!') {
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(SCANNING_PREFIX + name, startMark,
          "expected '!', but found " + s + "(" + (c) + ")", reader.getMark());
    }
    // Look for the next '!' in the stream, stopping if we hit a
    // non-word-character. If the first character is a space, then the
    // tag-handle is a c-primary-tag-handle ('!').
    int length = 1;
    c = reader.peek(length);
    if (c != ' ') {
      // Scan through 0+ alphabetic characters.
      // According to the specification, these should be
      // ns-word-char only, which prohibits '_'. This might be a
      // candidate for a configuration option.
      while (CharConstants.ALPHA.has(c)) {
        length++;
        c = reader.peek(length);
      }
      // Found the next non-word-char. If this is not a space and not an
      // '!', then this is an error, as the tag-handle was specified as:
      // !(name) or similar; the trailing '!' is missing.
      if (c != '!') {
        reader.forward(length);
        final String s = String.valueOf(Character.toChars(c));
        throw new ScannerException(SCANNING_PREFIX + name, startMark,
            "expected '!', but found " + s + "(" + (c) + ")", reader.getMark());
      }
      length++;
    }
    return reader.prefixForward(length);
  }

  /**
   * Scan a Tag URI. This scanning is valid for both local and global tag directives, because both
   * appear to be valid URIs as far as scanning is concerned. The difference may be distinguished
   * later, in parsing. This method will scan for ns-uri-char*, which covers both cases.
   * <p>
   * This method performs no verification that the scanned URI conforms to any particular kind of
   * URI specification.
   */
  String scanTagUri(String name, CharConstants range, Optional<Mark> startMark) {
    // See the specification for details.
    // Note: we do not check if URI is well-formed.
    StringBuilder chunks = new StringBuilder();
    // Scan through accepted URI characters, which includes the standard
    // URI characters, plus the start-escape character ('%'). When we get
    // to a start-escape, scan the escaped sequence, then return.
    int length = 0;
    int c = reader.peek(length);
    while (range.has(c)) {
      if (c == '%') {
        chunks.append(reader.prefixForward(length));
        length = 0;
        chunks.append(scanUriEscapes(name, startMark));
      } else {
        length++;
      }
      c = reader.peek(length);
    }
    // Consume the last "chunk", which would not otherwise be consumed by
    // the loop above.
    if (length != 0) {
      chunks.append(reader.prefixForward(length));
    }
    if (chunks.length() == 0) {
      // If no URI was found, an error has occurred.
      final String s = String.valueOf(Character.toChars(c));
      throw new ScannerException(SCANNING_PREFIX + name, startMark,
          "expected URI, but found " + s + "(" + (c) + ")", reader.getMark());
    }
    return chunks.toString();
  }

  /**
   * <p>
   * Scan a sequence of %-escaped URI escape codes and convert them into a String representing the
   * unescaped values.
   * </p>
   * <p>
   * This method fails for more than 256 bytes' worth of URI-encoded characters in a row. Is this
   * possible? Is this a use-case?
   */
  String scanUriEscapes(String name, Optional<Mark> startMark) {
    // First, look ahead to see how many URI-escaped characters we should
    // expect, so we can use the correct buffer size.
    int length = 1;
    while (reader.peek(length * 3) == '%') {
      length++;
    }
    // See the specification for details.
    // URIs containing 16 and 32 bit Unicode characters are
    // encoded in UTF-8, and then each octet is written as a
    // separate character.
    Optional<Mark> beginningMark = reader.getMark();
    ByteBuffer buff = ByteBuffer.allocate(length);
    while (reader.peek() == '%') {
      reader.forward();
      try {
        byte code = (byte) Integer.parseInt(reader.prefix(2), 16);
        buff.put(code);
      } catch (NumberFormatException nfe) {
        int c1 = reader.peek();
        final String s1 = String.valueOf(Character.toChars(c1));
        int c2 = reader.peek(1);
        final String s2 = String.valueOf(Character.toChars(c2));
        throw new ScannerException(SCANNING_PREFIX + name, startMark,
            "expected URI escape sequence of 2 hexadecimal numbers, but found " + s1 + "(" + c1
                + ") and " + s2 + "(" + c2 + ")",
            reader.getMark());
      }
      reader.forward(2);
    }
    buff.flip();
    try {
      return UriEncoder.decode(buff);
    } catch (CharacterCodingException e) {
      throw new ScannerException(SCANNING_PREFIX + name, startMark,
          "expected URI in UTF-8: " + e.getMessage(), beginningMark);
    }
  }

  /**
   * Scan a line break, transforming:
   *
   * <pre>
   * '\r\n'   : '\n'
   * '\r'     : '\n'
   * '\n'     : '\n'
   * '\x85'   : '\n'
   * '\u2028' : '\u2028'
   * '\u2029  : '\u2029'
   * default : ''
   * </pre>
   *
   * @return transformed character or empty string if no line break detected
   */
  Optional<String> scanLineBreak() {
    int c = reader.peek();
    if (c == '\r' || c == '\n' || c == '\u0085') {
      if (c == '\r' && '\n' == reader.peek(1)) {
        reader.forward(2);
      } else {
        reader.forward();
      }
      return Optional.of("\n");
    } else if (c == '\u2028' || c == '\u2029') {
      reader.forward();
      return Optional.of(String.valueOf(Character.toChars(c)));
    }
    return Optional.empty();
  }

  //endregion

  /**
   * Ignore Comment token if they are null, or Comments should not be parsed
   *
   * @param tokens - token types
   * @return tokens to be used
   */
  List<Token> makeTokenList(Token... tokens) {
    List<Token> tokenList = new ArrayList<>();
    for (Token token : tokens) {
      if (token == null) {
        continue;
      }
      if (!settings.parseComments && (token instanceof CommentToken)) {
        continue;
      }
      tokenList.add(token);
    }
    return tokenList;
  }
  //endregion

  @Override
  public void resetDocumentIndex() {
    this.reader.resetDocumentIndex();
  }

}
