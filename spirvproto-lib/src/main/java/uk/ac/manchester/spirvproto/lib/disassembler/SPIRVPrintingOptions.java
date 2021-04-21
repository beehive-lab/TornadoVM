package uk.ac.manchester.spirvproto.lib.disassembler;

public class SPIRVPrintingOptions {
    public final SPIRVSyntaxHighlighter highlighter;
    public final int indent;
    public final boolean shouldInlineNames;
    public final boolean shouldGroup;

    public SPIRVPrintingOptions(SPIRVSyntaxHighlighter highlighter, int indent, boolean shouldInlineNames, boolean shouldGroup) {
        this.highlighter = highlighter;
        this.indent = indent;
        this.shouldInlineNames = shouldInlineNames;
        this.shouldGroup = shouldGroup;
    }
}
