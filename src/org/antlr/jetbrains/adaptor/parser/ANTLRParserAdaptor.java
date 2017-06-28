package org.antlr.jetbrains.adaptor.parser;

import com.intellij.lang.*;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

import org.antlr.jetbrains.adaptor.lexer.PSITokenSource;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.PredictionMode;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.jetbrains.annotations.NotNull;

/**
 * An adaptor that makes an ANTLR parser look like a PsiParser.
 */
public abstract class ANTLRParserAdaptor implements PsiParser {
    protected final Language language;
    protected final Parser parser;
    public ASTNode oldTree = null;
    public CharSequence oldText = null;
    public CharSequence newText = null;
    public TextRange actuallyReparsedNew = null;

    /**
     * Create a jetbrains adaptor for an ANTLR parser object. When
     * the IDE requests a {@link #parse(IElementType, PsiBuilder)},
     * the token stream will be set on the parser.
     */
    public ANTLRParserAdaptor(Language language, Parser parser) {
        this.language = language;
        this.parser = parser;
    }

    public Language getLanguage() {
        return language;
    }

    /**
     * Parse without creating full AST
     *
     * @param root
     * @param builder
     */
    public void parseLight(IElementType root, PsiBuilder builder) {

        if (oldTree != null) {

        }

        ProgressIndicatorProvider.checkCanceled();
//		NonCancelableSection test = ProgressIndicatorProvider.startNonCancelableSectionIfSupported();
        TokenSource source = new PSITokenSource(builder);
        TokenStream tokens = new CommonTokenStream(source);
        parser.setTokenStream(tokens);
        parser.setErrorHandler(new ErrorStrategyAdaptor()); // tweaks missing tokens
        parser.removeErrorListeners();
        parser.addErrorListener(new SyntaxErrorListener()); // trap errors
        parser.addErrorListener(new ConsoleErrorListener());
//        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        ParseTree parseTree = null;
//        ((CommonTokenStream) parser.getTokenStream()).fill();
//        ((CommonTokenStream) parser.getTokenStream()).seek(0);
//        while (tokens.LA(1) != Token.EOF && parser.getCurrentToken().getType() != Token.EOF) {
            PsiBuilder.Marker rollbackMarker = builder.mark();
            try {
                parseTree = parse(parser, root);
            } finally {
                rollbackMarker.rollbackTo();
            }
            // Now convert ANTLR parser tree to PSI tree by mimicking subtree
            // enter/exit with mark/done calls. I *think* this creates their parse
            // tree (AST as they call it) when you call {@link PsiBuilder#getTreeBuilt}
            ANTLRParseTreeToPSIConverter listener = createListener(parser, root, builder);
            ParseTreeWalker.DEFAULT.walk(listener, parseTree);
//            while (parser.getCurrentToken().getType() != Token.EOF && parser.getCurrentToken().getType() != HlasmParser.ENDLINE) {
//                parser.getTokenStream().consume();
//                builder.advanceLexer();
//            }
//        }
        while (!builder.eof()) {
            ProgressIndicatorProvider.checkCanceled();
            builder.advanceLexer();
        }
        //todo if we do not reach end need to mark remaning tokens as errors

        // NOTE: parse tree returned from parse will be the
        // usual ANTLR tree ANTLRParseTreeToPSIConverter will
        // convert that to the analogous jetbrains AST nodes
        // When parsing an entire file, the root IElementType
        // will be a IFileElementType.
        //
        // When trying to rename IDs and so on, you get a
        // dummy root and a type arg identifier IElementType.
        // This results in a weird tree that has for example
        // (ID (expr (primary ID))) with the ID IElementType
        // as a subtree root as well as the appropriate leaf
        // all the way at the bottom.  The dummy ID root is a
        // CompositeElement and created by
        // ParserDefinition.createElement() despite having
        // being TokenIElementType.


    }

    @NotNull
    @Override
    public ASTNode parse(IElementType root, PsiBuilder builder) {
        long startTime = System.nanoTime();
        PsiBuilder.Marker rootMarker = builder.mark();

        parseLight(root, builder);
//		try {
//			Thread.sleep(50);
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
        rootMarker.done(root);
        if (root instanceof IFileElementType) {
            return builder.getTreeBuilt(); // calls the ASTFactory.createComposite() etc...
        } else {
            //((PsiBuilder.Marker)builder.getLatestDoneMarker()).setCustomEdgeTokenBinders(null,WhitespacesBinders.GREEDY_RIGHT_BINDER);
//			FlyweightCapableTreeStructure<LighterASTNode> tree = builder.getLightTree();
//			tree.get
//			System.out.println("reparsed until "+((LighterASTNode) builder.getLatestDoneMarker()).getEndOffset())
            builder.getLightTree();
            rootMarker.drop();
//			System.out.println("type:" + builder.getLatestDoneMarker().getTokenType());
//			System.out.println("reparsed text: " + builder.getOriginalText());

            System.out.println("reparse time " + root + (System.nanoTime() - startTime));
            return builder.getTreeBuilt();
        }
    }

    protected abstract ParseTree parse(Parser parser, IElementType root);

    protected ANTLRParseTreeToPSIConverter createListener(Parser parser, IElementType root, PsiBuilder builder) {
        return new ANTLRParseTreeToPSIConverter(language, parser, builder);
    }
}
