package uk.ac.manchester.spirvproto.lib.assembler;

class SPIRVToken {
    String value;
    SPIRVTokenType type;

    public SPIRVToken(String token, SPIRVInstRecognizer recognizer) {
        value = token.replace("\\s", "");

        if (value.equals("="))
            type = SPIRVTokenType.ASSIGN;
        else if (value.startsWith(";"))
            type = SPIRVTokenType.COMMENT;
        else if (value.startsWith("%"))
            type = SPIRVTokenType.ID;
        else if (recognizer.isInstruction(token))
            type = SPIRVTokenType.INSTRUCTION;
        else
            type = SPIRVTokenType.IMMEDIATE;
    }

    @Override
    public String toString() {
        return "(" + value + " " + type + ")";
    }

    public boolean isOperand() {
        return type == SPIRVTokenType.ID || type == SPIRVTokenType.IMMEDIATE;
    }
}
