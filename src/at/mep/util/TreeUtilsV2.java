package at.mep.util;

import at.mep.Matlab;
import at.mep.editor.tree.EAttributes;
import at.mep.editor.tree.MTreeNode;
import at.mep.meta.EAccess;
import com.mathworks.util.tree.Tree;
import com.mathworks.widgets.text.mcode.MTree;
import org.apache.commons.lang.Validate;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Scanner;

import static com.mathworks.widgets.text.mcode.MTree.NodeType.*;

// com.mathworks.widgets.text.mcode.MDocumentUtils

/** Created by Andreas Justin on 2017-09-29. */
public class TreeUtilsV2 {
    /** R2014a does not have MTree:getFileType */
    public static enum FileType {
        ScriptFile,
        FunctionFile,
        ClassDefinitionFile,
        Unknown;

        private FileType() {
        }
    }

    public static MTree.Node mTreeNodeGetClassName(MTree.Node node) {
        Validate.isTrue(node.getType() == CLASSDEF, node + " is not a ClassDef Node");
        MTree.Node n = node.getLeft().getRight();
        while (!EnumSet.of(ID, JAVA_NULL_NODE).contains(n.getType())) {
            n = n.getLeft();
        }
        return n;
    }

    public static MTree.Node mTreeNodeGetFunctionName(MTree.Node node) {
        return node.getFunctionName();
    }

    public static MTree.Node mTreeNodeGetPropertyName(MTree.Node node) {
        Validate.isTrue(node.getType() == EQUALS, node + " is not a Property (EUQLAS) Node");
        MTree.Node n = node;
        while (!EnumSet.of(ID, JAVA_NULL_NODE).contains(n.getType())) {
            n = n.getLeft();
        }
        return n;
    }

    /** support for older version R2014a does not have a "getFileType()" */
    public static TreeUtilsV2.FileType getFileType(MTree tree) {
        if (Matlab.verLessThan(Matlab.R2016b)) {
            if (tree.findAsList(CLASSDEF).size() > 0) {
                return FileType.ClassDefinitionFile;
            }
            if (tree.findAsList(FUNCTION).size() > 0) {
                return FileType.FunctionFile;
            }
            return FileType.ScriptFile;
        } else {
            return FileType.valueOf(tree.getFileType().toString());
        }
    }


    public static boolean mTreeNodeHasAttributes(MTree.Node node) {
        return mTreeNodeIsJavaNullNode(node.getLeft());
    }

    public static boolean mTreeNodeHasChildren(MTree.Node node) {
        return mTreeNodeIsJavaNullNode(node.getRight());
    }

    public static boolean mTreeNodeIsJavaNullNode(MTree.Node node) {
        return node.getType() == JAVA_NULL_NODE;
    }

    public static String stringForMTreeNodeType(MTree.NodeType type) {
        return EMTreeNodeTypeString.valueOf(type.name()).getDisplayString();
    }


    public static boolean hasChildren(Tree<MTree.Node> tree) {
        return tree.getChildCount(tree.getRoot()) > 0;
    }

    public static List<MTree.Node> findNode(List<MTree.Node> nodes, MTree.NodeType nodeType) {
        List<MTree.Node> list = new ArrayList<>(nodes.size());
        for (MTree.Node node : nodes) {
            if (node.getType() == nodeType) {
                list.add(node);
            }
        }
        return list;
    }

    public static List<MTree.Node> treeToArrayList(Tree<MTree.Node> tree) {
        int iMax = tree.getChildCount(tree.getRoot());
        List<MTree.Node> list = new ArrayList<>(iMax);
        for (int i = 0; i < iMax; i++) {
            list.add(tree.getChild(tree.getRoot(), i));
        }
        return list;
    }


    public static List<MTree.Node> searchForAttributes(MTree.Node tree) {
        List<MTree.Node> attributes = TreeUtilsV2.findNode(tree.getSubtree(), ATTRIBUTES);
        List<MTree.Node> attrs = new ArrayList<>(10);
        for (MTree.Node n : attributes) {
            List<MTree.Node> attributeBlock = n.getSubtree();
            for (MTree.Node mtNode : attributeBlock) {
                if (mtNode.getType() == ATTR) {
                    attrs.add(mtNode);
                }
            }
        }

        return attrs;
    }

    public static List<AttributeHolder> convertAttributes(List<MTree.Node> attributes) {
        List<AttributeHolder> list = new ArrayList<>(10);

        for (MTree.Node mtNodeAttr : attributes) {
            List<MTree.Node> attrs = mtNodeAttr.getSubtree();
            EAttributes eAttributes;

            switch (attrs.size()) {
                case 2:
                    // single definition e.g. (Transient):
                    // properties (Transient)
                    // properties (Transient, Access = private)
                    eAttributes = EAttributes.valueOf(attrs.get(1).getText().toUpperCase());
                    list.add(new AttributeHolder(mtNodeAttr, eAttributes, EAccess.TRUE));
                    break;
                case 3:
                    // definition e.g.:
                    // properties (Transient = true)
                    // properties (Transient = true, Access = private)
                    eAttributes = EAttributes.valueOf(attrs.get(1).getText().toUpperCase());
                    EAccess access = EAccess.INVALID;
                    if (attrs.get(2).getType() != INT){
                        access = EAccess.valueOf(attrs.get(2).getText().toUpperCase());
                    }
                    list.add(new AttributeHolder(mtNodeAttr, eAttributes, access));
                    break;
                default:
                    throw new IllegalStateException(
                            "unknown state for Attributes to have neither 2 or 3 fields, Editor.Line: "
                                    + mtNodeAttr.getStartLine());
            }
        }
        return list;
    }

    public static List<MTree.Node> searchForProperties(MTree.Node tree) {
        List<MTree.Node> properties = TreeUtilsV2.findNode(tree.getSubtree(), EQUALS);
        return properties;
    }

    public static List<PropertyHolder> convertProperties(List<MTree.Node> properties) {
        List<PropertyHolder> propertyHolders = new ArrayList<>(10);

        for (MTree.Node mtNodeProp : properties) {
            List<MTree.Node> prop = mtNodeProp.getSubtree();
            String name = "";
            String type = "";
            String validator = "";

            List<MTree.Node> atbase = TreeUtilsV2.findNode(prop, ATBASE);
            List<MTree.Node> prpdec = TreeUtilsV2.findNode(prop, PROPTYPEDECL);

            // only name, no atbase definition and nor declaration
            if (atbase.size() == 0 && prpdec.size() == 0 && prop.size() == 2 && prop.get(1).getType() == ID) {
                name = prop.get(1).getText();
            }

            // property is defined via "@" (legacy - undocumented)
            if (atbase.size() > 0) {
                name = atbase.get(0).getLeft().getText();
                type = "@" + atbase.get(0).getRight().getText();
            }

            // property is defined via new declaration method (e.g.: var double {validator1, validator2}
            if (prpdec.size() > 0) {
                // using MTreeNode for easier access to property attributes
                String str = MTreeNode.construct(prpdec.get(0), false).attributeString();

                // scanner is an cheap and easy way to extract name, type and validators
                Scanner scanner = new Scanner(str);
                name = scanner.next();
                str = str.replace(name, "");
                if (scanner.hasNext()) {
                    type = scanner.next();
                    str = str.replace(type, "");
                }
                if (scanner.hasNext()) {
                    str = StringUtils.trimStart(str);
                    validator = str;
                }
            }

            propertyHolders.add(new PropertyHolder(mtNodeProp, name, type, validator));
        }
        return propertyHolders;
    }

    public static List<MTree.Node> searchForMethods(MTree.Node tree) {
        return new ArrayList<>(0);
    }

    public static List<MTree.Node> searchForFunctions(MTree.Node tree) {
        return new ArrayList<>(0);
    }

    public static class ClassDefHolder {
        private MTree.Node node;
    }

    public static class AttributeHolder {
        private MTree.Node node;
        private EAttributes attribute;
        private EAccess access;

        public AttributeHolder(MTree.Node node, EAttributes attribute, EAccess access) {
            this.node = node;
            this.attribute = attribute;
            this.access = access;
        }

        public EAttributes getAttribute() {
            return attribute;
        }

        public EAccess getAccess() {
            if (access == null)
                return attribute.getDefaultAccess();

            return access;
        }
    }

    public static class PropertyHolder {
        private MTree.Node node;
        private String name = ": NAME NOT SET";
        private String type = ": TYPE NOT DEFINED";
        private String validator = ": VALIDATORS NOT DEFINED";

        public PropertyHolder(MTree.Node node, String name, String type, String validator) {
            this.node = node;
            this.name = name;
            this.type = type;
            this.validator = validator;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }

        public String getValidator() {
            return validator;
        }
    }

    private enum EMTreeNodeTypeString {
        ERROR("error"),
        IF("if"),
        ELSE("else"),
        ELSEIF("elseif"),
        SWITCH("switch"),
        WHILE("while"),
        BREAK("break"),
        RETURN("return"),
        GLOBAL("global"),
        PERSISTENT("persistent"),
        TRY("try"),
        CATCH("catch"),
        CONTINUE("continue"),
        FUNCTION("function"),
        FOR("for"),
        PARFOR("parfor"),
        LEFT_PAREN("("),
        RIGHT_PAREN(")"),
        LEFT_BRACKET("["),
        RIGHT_BRACKET("]"),
        LEFT_CURLY_BRACE("{"),
        RIGHT_CURLY_BRACE("}"),
        AT_SIGN("@"),
        DOT_LEFT_PAREN(".("),
        PLUS("+"),
        MINUS("-"),
        MULTIPLY("*"),
        DIVIDE("/"),
        LEFT_DIVIDE("\\"),
        EXPONENTIATION("^"),
        COLON(":"),
        DOT("."),
        DOT_MULTIPLY(".*"),
        DOT_DIVIDE("./"),
        DOT_LEFT_DIVIDE(".\\"),
        DOT_EXPONENTIATION(".^"),
        AND("&"),
        OR("|"),
        ANDAND("&&"),
        OROR("||"),
        LT("<"),
        GT(">"),
        LE("<="),
        GE(">="),
        EQ("="),
        NE("~="),
        CASE("case"),
        OTHERWISE("otherwise"),
        DUAL("dual"),
        TRANS("'"),
        DOTTRANS(".'"),
        NOT("~"),
        ID("id"),
        INT("int"),
        DOUBLE("double"),
        STRING("string"),
        SEMI(";"),
        COMMA(","),
        EOL("EOL"),
        BANG("bang"),
        END("end"),
        EQUALS("=="),
        CLASSDEF("classdef"),
        PROPERTIES("properties"),
        METHODS("methods"),
        EVENTS("events"),
        QUEST("?"),
        ENUMERATION("enum"),
        ERR("error"),
        CELL("cell"),
        SUBSCR("subscr"),
        CALL("call"),
        EXPR("expr"),
        PRINT_EXPR("print_expr"),
        ANON("anon"),
        ANONID("anonid"),
        DCALL("dcall"),
        JOIN("join"),
        LIST("list"),
        EVENT("event"),
        FIELD("field"),
        UMINUS("uminus"),
        UPLUS("uplus"),
        ATBASE("@"),
        CEXPR("cexpr"),
        ROW("row"),
        ATTR("attr"),
        ETC("ETC"),
        DISTFOR("distfor"),
        CELL_TITLE("%%"),
        COMMENT("%"),
        BLOCK_COMMENT("%{"),
        BLOCK_COMMENT_END("%}"),
        OLDFUN("oldfun"),
        PARENS("parens"),
        IFHEAD("ifhead"),
        PROTO("proto"),
        ATTRIBUTES("attributes"),
        SPMD("spmd"),
        PROPTYPEDECL("proptypedecl"),
        JAVA_NULL_NODE("javaNullNode");

        private final String displayString;

        EMTreeNodeTypeString(String displayString) {
            this.displayString = displayString;
        }

        public String getDisplayString() {
            return displayString;
        }
    }
}
