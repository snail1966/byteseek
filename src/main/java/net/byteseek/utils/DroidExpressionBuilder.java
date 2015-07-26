package net.byteseek.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Text;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by matt on 26/07/15.
 */
public class DroidExpressionBuilder {

    //TODO: missing syntax: {1-*}, ??

    public DroidExpressionBuilder() {};

    public ByteSequenceSpec build(String expression, String anchor) {
        ByteSequenceSpec byteSequence = new ByteSequenceSpec();
        byteSequence.anchor = checkAnchor(anchor);
        List<SubSequenceSpec> subSequences = getSubsequences(expression, anchor);
        for (SubSequenceSpec subSequence : subSequences) {
            byteSequence.subSequences.add(subSequence);
        }
        return byteSequence;
    }

    private String checkAnchor(String anchor) {
        if (anchor == null || anchor.isEmpty()) {
            return "BOFoffset";
        }
        if ("BOFoffset".equals(anchor) || "EOFoffset".equals(anchor) || "Variable".equals(anchor)) {
            return anchor;
        }
        throw new IllegalArgumentException("The anchor must be BOFoffset, EOFoffset or Variable.\n" +
                                           "A null or empty anchor is permitted and defaults to BOFoffset.\n" +
                                           "The actual anchor supplied was: " + anchor);
    }

    private List<SubSequenceSpec> getSubsequences(String expression, String anchor) {
        List<SubSequenceSpec> subSequences = new ArrayList<SubSequenceSpec>();
        String[] subSequenceExpressions = getSubsequenceExpressions(expression);
        String anchored = anchor;
        for (String subExpression : subSequenceExpressions) {
            subSequences.add(buildSubSequence(subExpression, anchored));
            anchored = null;
        }
        return subSequences;
    }

    private SubSequenceSpec buildSubSequence(String subExpression, String anchor) {
        SubSequenceSpec subSequence = new SubSequenceSpec();

        // If there are no offsets or alternatives, the sequence  needs no fragments.
        if (containsNoMandatoryFragments(subExpression)) {
            subSequence.mainExpression = subExpression; // just set the expression directly.
        } else {
            // Split into potential fragments and offsets
            List<String> offsetExpressions = splitFragments(subExpression);

            // If the expression is anchored, check for offsets next to the anchor,
            // as these are processed by setting min/max offsets rather than using fragments.
            processAnchoredOffsets(subSequence, anchor, offsetExpressions);

            // Process the fragments of the subsequence, and return the main expression:
            subSequence.mainExpression = processFragments(subSequence, offsetExpressions);
        }

        return subSequence;
    }

    private String processFragments(SubSequenceSpec subSequence, List<String> offsetExpressions) {
        String mainExpression;
        int numExpressions = offsetExpressions.size();
        if (numExpressions == 1) { // can be no fragments with only a single expression.
            mainExpression = offsetExpressions.get(0);
        } else {
            int mainExpressionIndex = getMainExpression(offsetExpressions);
            mainExpression = offsetExpressions.get(mainExpressionIndex);

            if (mainExpressionIndex > 0) { // have left fragments.
                addLeftFragments(subSequence, offsetExpressions, mainExpressionIndex);
            }

            if (mainExpressionIndex < numExpressions - 1) { // have right fragments.
                addRightFragments(subSequence, offsetExpressions, mainExpressionIndex);
            }
        }
        return mainExpression;
    }

    private void addLeftFragments(SubSequenceSpec subSequence, List<String> offsetExpressions, int mainExpressionIndex) {
        int position = 1;
        int fragMinOffset = 0;
        int fragMaxOffset = 0;
        for (int i = mainExpressionIndex -1; i >= 0; i--) {
            String fragmentExpression = offsetExpressions.get(i);
            if (isOffset(fragmentExpression)) {
                fragMinOffset = getMinOffset(fragmentExpression);
                fragMaxOffset = getMaxOffset(fragmentExpression);
            } else if (isAlternatives(fragmentExpression)) {
                List<FragmentSpec> alternatives = getAlternativeFragments(fragmentExpression, position);
                for (FragmentSpec fragment : alternatives) {
                    fragment.minFragOffset = fragMinOffset;
                    fragment.maxFragOffset = fragMaxOffset;
                    subSequence.leftFragments.add(fragment);
                }
                position++;
                fragMinOffset = 0;
                fragMaxOffset = 0;
            } else {
                FragmentSpec fragment = new FragmentSpec();
                fragment.position = position;
                fragment.fragmentExpression = fragmentExpression;
                fragment.minFragOffset = fragMinOffset;
                fragment.maxFragOffset = fragMaxOffset;
                subSequence.leftFragments.add(fragment);
                position++;
                fragMinOffset = 0;
                fragMaxOffset = 0;
            }
        }
    }

    private void addRightFragments(SubSequenceSpec subSequence, List<String> offsetExpressions, int mainExpressionIndex) {
        int position = 1;
        int fragMinOffset = 0;
        int fragMaxOffset = 0;
        for (int i = mainExpressionIndex + 1; i < offsetExpressions.size(); i++) {
            String fragmentExpression = offsetExpressions.get(i);
            if (isOffset(fragmentExpression)) {
                fragMinOffset = getMinOffset(fragmentExpression);
                fragMaxOffset = getMaxOffset(fragmentExpression);
            } else if (isAlternatives(fragmentExpression)) {
                List<FragmentSpec> alternatives = getAlternativeFragments(fragmentExpression, position);
                for (FragmentSpec fragment : alternatives) {
                    fragment.minFragOffset = fragMinOffset;
                    fragment.maxFragOffset = fragMaxOffset;
                    subSequence.rightFragments.add(fragment);
                }
                position++;
                fragMinOffset = 0;
                fragMaxOffset = 0;
            } else {
                FragmentSpec fragment = new FragmentSpec();
                fragment.position = position;
                fragment.fragmentExpression = fragmentExpression;
                fragment.minFragOffset = fragMinOffset;
                fragment.maxFragOffset = fragMaxOffset;
                subSequence.rightFragments.add(fragment);
                position++;
                fragMinOffset = 0;
                fragMaxOffset = 0;
            }
        }
    }

    private List<FragmentSpec> getAlternativeFragments(String fragmentExpression, int position) {
        List<FragmentSpec> fragments = new ArrayList<FragmentSpec>();
        String stripBrackets = fragmentExpression.substring(1, fragmentExpression.length() - 1);
        String[] alternatives = stripBrackets.split("\\|");
        for (String alternative : alternatives) {
            FragmentSpec fragment = new FragmentSpec();
            fragment.position = position;
            fragment.fragmentExpression = alternative.trim();
            fragments.add(fragment);
        }
        return fragments;
    }

    /**
     * Figures out which of the expressions should be the main expression to search for.
     * The rest of them will be fragments to the left or right of the main expression.
     *
     * @param offsetExpressions
     * @return
     */
    private int getMainExpression(List<String> offsetExpressions) {
        int mainIndex = -1;
        int longest = 0;
        for (int expressionIndex = 0; expressionIndex < offsetExpressions.size(); expressionIndex++) {
            String expression = offsetExpressions.get(expressionIndex);
            if (expression.charAt(0) != '(' && expression.charAt(0) != '{') {
                int numBytes = getNumBytesInExpression(expression);
                if (numBytes > longest) {
                    mainIndex = expressionIndex;
                    longest = numBytes;
                }
            }
        }

        if (mainIndex == -1) {
            String expressions = "";
            for (String expression : offsetExpressions) {
                expressions = expressions + expression + "\n";
            }
            throw new IllegalArgumentException("No expressions had any bytes which could be searched for:\n" + expressions);
        }

        return mainIndex;
    }

    /**
     * Processes an expression to count the number of bytes represented by it.
     *
     * @param expression
     * @return
     */
    private int getNumBytesInExpression(String expression) {
        int numBytes = 0;
        int hexCount = 0;
        int questionCount = 0;
        boolean inSet = false;
        boolean inString = false;
        boolean inCaseString = false;
        for (int i = 0; i < expression.length(); i++) {
            char currentChar = expression.charAt(i);
            if (inSet) {
                if (currentChar == ']') {
                    inSet = false;
                    numBytes++;
                }
            } else if (inString) {
                if (currentChar == '\'') {
                    inString = false;
                } else {
                    numBytes++;
                }
            } else if (inCaseString) {
                if (currentChar == '`') {
                    inCaseString = false;
                } else {
                    numBytes++;
                }
            } else {
                if (StringUtils.isHexDigit(currentChar)) {
                    hexCount++;
                    if (hexCount == 2) {
                        numBytes++;
                        hexCount = 0;
                    }
                } else if (hexCount == 1) {
                    throw new IllegalArgumentException("A hex digit was split in two in the expression: " + expression);
                } else if (currentChar == '?') {
                    questionCount++;
                    if (questionCount == 2) {
                        numBytes++;
                        questionCount = 0;
                    }
                } else if (questionCount == 1) {
                    throw new IllegalArgumentException("Questions marks must be in pairs, the expression only has a single one: " + expression);
                } else if (currentChar == '[') {
                    inSet = true;
                } else if (currentChar == '\'') {
                    inString = true;
                } else if (currentChar == '`') {
                    inCaseString = true;
                }
            }
        }
        return numBytes;
    }

    //TODO: deal with {1-*} syntax.  This causes us to have a min offset, then a new subsequence.
    private void processAnchoredOffsets(SubSequenceSpec subSequence, String anchor, List<String> offsetExpressions) {
        String firstOffset = offsetExpressions.get(0);
        int lastIndex      = offsetExpressions.size() - 1;
        String lastOffset  = offsetExpressions.get(lastIndex);

        if ("BOFoffset".equals(anchor) && isOffsetExpression(firstOffset)) {
            setSequenceOffsets(subSequence, firstOffset);
            offsetExpressions.remove(0);
        } else if ("EOFoffset".equals(anchor) && isOffsetExpression(lastOffset)) {
            setSequenceOffsets(subSequence, lastOffset);
            offsetExpressions.remove(lastIndex);
        } else if ("Variable".equals(anchor) && isOffsetExpression(offsetExpressions.get(0))) {
            offsetExpressions.remove(0); // an offset expression starting a variable sequence means nothing - it's already doing a wildcard search.
        }
    }

    private int getMinOffset(String fragmentExpression) {
        if (fragmentExpression.contains("-")) {
            int rangePosition = fragmentExpression.indexOf("-");
            return StringUtils.getInt(fragmentExpression, 1, rangePosition);
        } else {
            return StringUtils.getInt(fragmentExpression, 1, fragmentExpression.length() - 1);
        }
    }

    private int getMaxOffset(String fragmentExpression) {
        if (fragmentExpression.contains("-")) {
            int rangePosition = fragmentExpression.indexOf("-");
            return StringUtils.getInt(fragmentExpression, rangePosition + 1, fragmentExpression.length() - 1);
        } else {
            return StringUtils.getInt(fragmentExpression, 1, fragmentExpression.length() - 1);
        }
    }

    private void setSequenceOffsets(SubSequenceSpec subSequence, String offset) {
        if (offset.contains("-")) {
            int rangePosition = offset.indexOf("-");
            subSequence.minSeqOffset = StringUtils.getInt(offset, 1, rangePosition);
            subSequence.maxSeqOffset = StringUtils.getInt(offset, rangePosition + 1, offset.length() - 1);
        } else {
            int offsetValue = StringUtils.getInt(offset, 1, offset.length() - 1);
            subSequence.minSeqOffset = offsetValue;
            subSequence.maxSeqOffset = offsetValue;
        }
    }

    private boolean isOffsetExpression(String s) {
        return s.startsWith("{");
    }

    private boolean isAlternatives(String fragmentExpression) {
        return fragmentExpression.startsWith("(");
    }

    private boolean isOffset(String fragmentExpression) {
        return fragmentExpression.startsWith("{");
    }

    private List<String> splitFragments(String subExpression) {
        List<String> offsets = new ArrayList<String>();
        int expressionStart = 0;
        for (int charIndex = 0; charIndex < subExpression.length(); charIndex++) {
            char currentChar = subExpression.charAt(charIndex);
            if (currentChar == '{') {
                if (expressionStart < charIndex - 1) {
                    offsets.add(subExpression.substring(expressionStart, charIndex).trim());
                }
                expressionStart = charIndex;
            } else if (currentChar == '}') {
                offsets.add(subExpression.substring(expressionStart, charIndex + 1).trim());
                expressionStart = charIndex + 1;
            } else if (currentChar == '(') {
                if (expressionStart < charIndex - 1) {
                    offsets.add(subExpression.substring(expressionStart, charIndex).trim());
                }
                expressionStart = charIndex;
            } else if (currentChar == ')') {
                offsets.add(subExpression.substring(expressionStart, charIndex + 1).trim());
                expressionStart = charIndex + 1;
            }
        }
        if (expressionStart < subExpression.length() -1) {
            offsets.add(subExpression.substring(expressionStart).trim());
        }
        return offsets;
    }

    private boolean containsNoMandatoryFragments(String subExpression) {
        return !(subExpression.contains("{") | subExpression.contains("("));
    }


    private String[] getSubsequenceExpressions(String expression) {
        // Find any {1-*} expressions and convert into {1} * form:
        expression = expression.replaceAll("-\\*}", "} *");

        // Split expression into separate bytes sequences, separated by wildcards:
        String[] sequences = expression.split("\\*");

        // Trim spaces from start and end of sequences:
        for (int i = 0; i < sequences.length; i++) {
            sequences[i] = sequences[i].trim();
        }

        return sequences;
    }

    public static class ByteSequenceSpec {
        public String anchor;
        public List<SubSequenceSpec> subSequences = new ArrayList<SubSequenceSpec>();

        public String toDROIDXML() throws Exception {
            StringBuilder builder = new StringBuilder(2048);
            builder.append("<ByteSequence Reference=\"").append(anchor).append("\">\n");
            int subSequencePosition = 1;
            for (SubSequenceSpec subSequence : subSequences) {
                subSequence.toDROIDXML(builder, subSequencePosition++);
            }
            builder.append("</ByteSequence>\n");
            return builder.toString();
        }
    }

    public static class SubSequenceSpec {
        public String mainExpression;
        public int minSeqOffset;
        public int maxSeqOffset;
        public List<FragmentSpec> leftFragments = new ArrayList<FragmentSpec>();
        public List<FragmentSpec> rightFragments = new ArrayList<FragmentSpec>();

        public void toDROIDXML(StringBuilder builder, int position) throws Exception {
            builder.append("\t<SubSequence Position=\"").append(position).append("\" ");
            builder.append("SubSeqMaxOffset=\"").append(maxSeqOffset).append("\" ");
            builder.append("SubSeqMinOffset=\"").append(minSeqOffset).append("\">\n");
            builder.append("\t\t<Sequence>").append(StringUtils.escapeXml(mainExpression)).append("</Sequence>\n");
            for (FragmentSpec fragment : leftFragments) {
                fragment.toDROIDXML(builder, "LeftFragment");
            }
            for (FragmentSpec fragment : rightFragments) {
                fragment.toDROIDXML(builder, "RightFragment");
            }
            builder.append("\t</SubSequence>\n");
        }
    }

    public static class FragmentSpec {
        public int position;
        public String fragmentExpression;
        public int minFragOffset;
        public int maxFragOffset;

        public void toDROIDXML(StringBuilder builder, String elementName) throws Exception {
            builder.append("\t\t<").append(elementName).append(' ');
            builder.append("MaxOffset=\"").append(maxFragOffset).append("\" ");
            builder.append("MinOffset=\"").append(minFragOffset).append("\" ");
            builder.append("Position=\"").append(position).append("\">");
            builder.append(StringUtils.escapeXml(fragmentExpression));
            builder.append("</").append(elementName).append(">\n");
        }
    }

}