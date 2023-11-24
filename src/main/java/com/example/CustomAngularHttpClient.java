package com.example;

import io.swagger.codegen.languages.TypeScriptAngularClientCodegen;

public class CustomAngularHttpClient extends TypeScriptAngularClientCodegen {


    CustomAngularHttpClient() {
        super();
        this.embeddedTemplateDir = this.templateDir = "custom-http-client";
    }

    @Override
    public String getName() {
        return "custom-typescript-angular";
    }

    public String getHelp() {
        return "Generates a Custom TypeScript Angular (2.x - 5.x) client library.";
    }
}
