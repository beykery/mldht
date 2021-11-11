/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.io.InputStream;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;


public class ConfigReader {

    public static class ParseException extends RuntimeException {
        public ParseException(Exception cause) {
            super(cause);
        }
    }

    InputStream defaults;
    Document current;

    public ConfigReader(InputStream in) {
        this.defaults = in;
    }

    public Document read() {
        if (current == null)
            readConfig();
        return current;
    }

    void readConfig() {
        current = readFile();
    }

    public Optional<String> get(XPathExpression expr) {
        Node result;
        try {
            result = (Node) expr.evaluate(current, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        if (result == null)
            return Optional.empty();
        return Optional.of(result.getTextContent());
    }

    public Optional<Boolean> getBoolean(String path) {
        return get(XMLUtils.buildXPath(path)).map(str -> str.equals("true") || str.equals("1"));
    }

    public Optional<Long> getLong(String path) {
        return get(XMLUtils.buildXPath(path)).map(Long::valueOf);
    }

    public Stream<String> getAll(XPathExpression path) {
        NodeList result;
        try {
            result = (NodeList) path.evaluate(current, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
        if (result == null)
            return Stream.empty();
        return IntStream.range(0, result.getLength()).mapToObj(result::item).map(Node::getTextContent);
    }

    Document readFile() {
        Document document = null;
        try {
            // parse an XML document into a DOM tree
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder parser = factory.newDocumentBuilder();
            document = parser.parse(defaults);
        } catch (Exception e) {
            throw new ParseException(e);
        }
        return document;
    }
}
