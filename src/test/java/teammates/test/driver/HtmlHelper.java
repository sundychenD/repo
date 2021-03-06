package teammates.test.driver;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringReader;

import org.cyberneko.html.parsers.DOMParser;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import teammates.common.util.Config;

public class HtmlHelper {
    
    private static final String INDENTATION_STEP = "   ";

    /**
     * Verifies that two HTML files are logically equivalent, e.g. ignores
     * differences in whitespace and attribute order. If the assertion fails,
     * <code>AssertionError</code> will be thrown and the difference can then be traced.
     * @param expectedString the expected string for comparison
     * @param actualString the actual string for comparison
     * @param isPart if true, ignores top-level HTML tags, i.e <code>&lt;html&gt;</code>,
     *               <code>&lt;head&gt;</code>, and <code>&lt;body&gt;</code>
     */
    public static boolean assertSameHtml(String expected, String actual, boolean isPart) {
        return assertSameHtml(expected, actual, isPart, true);
    }
    
    /**
     * Verifies that two HTML files are logically equivalent, e.g. ignores
     * differences in whitespace and attribute order.
     * @param expectedString the expected string for comparison
     * @param actualString the actual string for comparison
     * @param isPart if true, ignores top-level HTML tags, i.e <code>&lt;html&gt;</code>,
     *               <code>&lt;head&gt;</code>, and <code>&lt;body&gt;</code>
     */
    public static boolean areSameHtml(String expected, String actual, boolean isPart) {
        return assertSameHtml(expected, actual, isPart, false);
    }
    
    private static boolean assertSameHtml(String expected, String actual, boolean isPart,
                                          boolean isDifferenceToBeShown) {
        String processedExpected = convertToStandardHtml(expected, isPart);
        String processedActual = convertToStandardHtml(actual, isPart);

        if (!AssertHelper.isContainsRegex(processedExpected, processedActual)) {
            if (isDifferenceToBeShown) {
                assertEquals("<expected>\n" + processedExpected + "</expected>",
                             "<actual>\n" + processedActual + "</actual>");
            }
            return false;
        }
        return true;
    }

    /**
     * Transform the HTML text to follow a standard format. 
     * Element attributes are reordered in alphabetical order.
     * Spacing and line breaks are standardized too.
     * @param rawHtml the raw HTML string to be converted
     * @param isPart if true, ignores top-level HTML tags, i.e <code>&lt;html&gt;</code>,
     *               <code>&lt;head&gt;</code>, and <code>&lt;body&gt;</code>
     * @return converted HTML string
     */
    public static String convertToStandardHtml(String rawHtml, boolean isPart) {
        try {
            Node currentNode = getNodeFromString(rawHtml);
            String initialIndentation = INDENTATION_STEP; // TODO start from zero indentation
            return convertToStandardHtmlRecursively(currentNode, initialIndentation, isPart);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Node getNodeFromString(String string) throws SAXException, IOException {
        DOMParser parser = new DOMParser();
        parser.parse(new InputSource(new StringReader(string)));
        return parser.getDocument();
    }

    private static String convertToStandardHtmlRecursively(Node currentNode, String indentation,
                                                           boolean isPart) {
        
        if (currentNode.getNodeType() == Node.TEXT_NODE) {
            String text = currentNode.getNodeValue().trim();
            return text.isEmpty() ? "" : indentation + text + "\n";
        } else if (isToolTip(currentNode)
                   || isPopOver(currentNode)
                   || (Config.STUDENT_MOTD_URL.isEmpty() && isMotdWrapper(currentNode))) {
            return "";
        } else if (isMotdContainer(currentNode)) {
            return indentation + "${studentmotd.container}\n";
        }

        StringBuilder currentHtmlText = new StringBuilder();
        String currentNodeName = currentNode.getNodeName().toLowerCase();
        boolean shouldIncludeCurrentNode = shouldIncludeCurrentNode(isPart, currentNode);

        if (shouldIncludeCurrentNode) {
            String nodeOpeningTag = indentation + getNodeOpeningTag(currentNode);
            currentHtmlText.append(nodeOpeningTag);
        }
        if (isVoidElement(currentNodeName)) {
            return currentHtmlText.toString();
        }
        
        String nodeContent = getNodeContent(currentNode,
                                            indentation + (shouldIncludeCurrentNode ? INDENTATION_STEP : ""),
                                            isPart);
        currentHtmlText.append(nodeContent);
        
        if (shouldIncludeCurrentNode) {
            String nodeClosingTag = indentation + getNodeClosingTag(currentNodeName);
            currentHtmlText.append(nodeClosingTag);
        }
        
        return currentHtmlText.toString();
    }

    /**
     * Ignores all non-{@link Element} {@link Node}s which include <code>#comment</code>,
     * <code>#document</code>, and <code>doctype</code>.<br>
     * In addition, if <code>isPart</code> (i.e only partial HTML checking is done),
     * ignores the top-level HTML tags, i.e <code>&lt;html&gt;</code>, <code>&lt;head&gt;</code>,
     * and <code>&lt;body&gt;</code>
     */
    private static boolean shouldIncludeCurrentNode(boolean isPart, Node currentNode) {
        if (currentNode.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        } else {
            String currentNodeName = currentNode.getNodeName().toLowerCase();
            return !(isPart && (currentNodeName.equals("html")
                                || currentNodeName.equals("head")
                                || currentNodeName.equals("body")));
        }
    }

    /**
     * Checks for tooltips (i.e any <code>div</code> with class <code>tooltip</code> in it)
     */
    private static boolean isToolTip(Node currentNode) {
        return checkForNodeWithSpecificAttributeValue(currentNode, "div", "class", "tooltip");
    }
    
    /**
     * Checks for popovers (i.e any <code>div</code> with class <code>popover</code> in it)
     */
    private static boolean isPopOver(Node currentNode) {
        return checkForNodeWithSpecificAttributeValue(currentNode, "div", "class", "popover");
    }
    
    /**
     * Checks for Message of the Day (MOTD) wrapper (i.e a <code>div</code> with id
     * <code>student-motd-wrapper</code>).
     */
    private static boolean isMotdWrapper(Node currentNode) {
        return checkForNodeWithSpecificAttributeValue(currentNode, "div", "id", "student-motd-wrapper");
    }
    
    /**
     * Checks for Message of the Day (MOTD) container (i.e a <code>div</code> with id
     * <code>student-motd-container</code>).
     */
    private static boolean isMotdContainer(Node currentNode) {
        return checkForNodeWithSpecificAttributeValue(currentNode, "div", "id", "student-motd-container");
    }
    
    private static boolean checkForNodeWithSpecificAttributeValue(Node currentNode, String nodeType,
                                                                  String attrType, String attrValue) {
        if (currentNode.getNodeName().equalsIgnoreCase(nodeType)) {
            NamedNodeMap attributes = currentNode.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                if (attribute.getNodeName().equalsIgnoreCase(attrType)
                        && attribute.getNodeValue().contains(attrValue)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    private static String getNodeOpeningTag(Node currentNode) {
        StringBuilder openingTag = new StringBuilder();
        // add the start of opening tag
        openingTag.append("<" + currentNode.getNodeName().toLowerCase());
        
        // add the attributes of the tag (getAttributes() returns the attributes sorted alphabetically)
        NamedNodeMap attributes = currentNode.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            openingTag.append(" " + attribute.getNodeName().toLowerCase() + "="
                                  + "\"" + attribute.getNodeValue().replace("\"", "&quot;") + "\"");
        }
        
        // close the tag
        openingTag.append(getEndOfOpeningTag(currentNode) + "\n");
        return openingTag.toString();
    }
    
    private static String getNodeContent(Node currentNode, String indentation, boolean isPart) {
        StringBuilder nodeContent = new StringBuilder();
        NodeList childNodes = currentNode.getChildNodes();
        // recursively add contents of the child nodes
        for (int i = 0; i < childNodes.getLength(); i++) {
            String childNode = convertToStandardHtmlRecursively(childNodes.item(i), indentation, isPart);
            nodeContent.append(childNode);
        }
        return nodeContent.toString();
    }
    
    private static String getNodeClosingTag(String currentNodeName) {
        return "</" + currentNodeName + ">\n";
    }

    // TODO remove this method and use > for all cases, as defined in our style guide
    private static String getEndOfOpeningTag(Node node) {
        String tagName = node.getNodeName().toLowerCase();
        if(isVoidElement(tagName)){
            return "/>";
        }else {
            return ">";
        }
    }
    
    private static boolean isVoidElement(String elementName){
        return elementName.equals("area")
                || elementName.equals("base")
                || elementName.equals("br")
                || elementName.equals("col")
                || elementName.equals("command")
                || elementName.equals("embed")
                || elementName.equals("hr")
                || elementName.equals("img")
                || elementName.equals("input")
                || elementName.equals("keygen")
                || elementName.equals("link")
                || elementName.equals("meta")
                || elementName.equals("param")
                || elementName.equals("source")
                || elementName.equals("track")
                || elementName.equals("wbr");
    }

}
