package com.wcp.readassist;

public class DefinitionItem {
    private String mWordType;
    private String mDefinition;

    public String getWordType() {
        return mWordType;
    }

    public String getDefinition() {
        return mDefinition;
    }

    public void setWordType(String wordType) {
        this.mWordType = wordType;
    }

    public void setDefinition(String definition) {
        this.mDefinition = definition.replaceAll("\n+", "");
    }
}
