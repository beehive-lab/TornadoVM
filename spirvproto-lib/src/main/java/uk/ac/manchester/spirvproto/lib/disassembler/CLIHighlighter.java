package uk.ac.manchester.spirvproto.lib.disassembler;

public class CLIHighlighter implements SPIRVSyntaxHighlighter {
    String IDFormat = "\033[1;33m%s\033[0m";
    String stringFormat = "\033[0;32m%s\033[0m";
    String intFormat = "\033[0;34m%s\033[0m";
    String commentFormat = "\u001b[38;5;246m%s\033[0m";

    private final boolean shouldHighlight;

    public CLIHighlighter(boolean shouldHighlight) {
        this.shouldHighlight = shouldHighlight;
    }

    @Override
    public String highlightId(String ID) {
        if (shouldHighlight) return String.format(IDFormat, ID);
        else return ID;
    }

    @Override
    public String highlightString(String string) {
        if (shouldHighlight) return String.format(stringFormat, string);
        else return string;
    }

    @Override
    public String highlightInt(String integer) {
        if (shouldHighlight) return String.format(intFormat, integer);
        else return integer;
    }

    @Override
    public String highlightComment(String comment) {
        if (shouldHighlight) return String.format(commentFormat, comment);
        else return comment;
    }
}
