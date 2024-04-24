package com.company.customcodegen;

import io.swagger.codegen.v3.*;
import io.swagger.codegen.v3.generators.typescript.AbstractTypeScriptClientCodegen;
import io.swagger.codegen.v3.utils.SemVer;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class CustomSwaggerClientCodegen extends AbstractTypeScriptClientCodegen {

    private static Logger logger = LoggerFactory.getLogger(CustomSwaggerClientCodegen.class);

    private static final SimpleDateFormat SNAPSHOT_SUFFIX_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");

    public static final String NPM_NAME = "npmName";
    public static final String NPM_VERSION = "npmVersion";
    public static final String NPM_REPOSITORY = "npmRepository";
    public static final String SNAPSHOT = "snapshot";
    public static final String WITH_INTERFACES = "withInterfaces";
    public static final String NG_VERSION = "ngVersion";
    public static final String NG_PACKAGR = "useNgPackagr";
    public static final String PROVIDED_IN_ROOT = "providedInRoot";
    public static final String KEBAB_FILE_NAME = "kebab-file-name";
    public static final String USE_OVERRIDE = "useOverride";

    protected String npmName = null;
    protected String npmVersion = "1.0.0";
    protected String npmRepository = null;
    protected boolean kebabFileNaming;

    private List<String> validHttpMethods = Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS",
            "HEAD",
            "PATCH"
    );

    public CustomSwaggerClientCodegen() {
        super();
        this.outputFolder = "generated-code" + File.separator + "typescript-angular";
        configureCLIOptions();
    }

    private void configureCLIOptions() {
        this.cliOptions.add(
                new CliOption(
                        NPM_NAME,
                        "The name under which you want to publish generated npm package"
                )
        );

        this.cliOptions.add(
                new CliOption(
                        NPM_VERSION,
                        "The version of your npm package")
        );

        this.cliOptions.add(
                new CliOption(
                        NPM_REPOSITORY,
                        "Use this property to set an url your private npmRepo in the package.json"
                )
        );

        this.cliOptions.add(
                new CliOption(
                        SNAPSHOT,
                        "When setting this property to true the version will be suffixed with -SNAPSHOT.yyyyMMddHHmm",
                        SchemaTypeUtil.BOOLEAN_TYPE
                )
                        .defaultValue(
                                Boolean.FALSE.toString()
                        )
        );

        this.cliOptions.add(
                new CliOption(
                        WITH_INTERFACES,
                        "Setting this property to true will generate interfaces next to the default class implementations.",
                        SchemaTypeUtil.BOOLEAN_TYPE
                )
                        .defaultValue(
                                Boolean.FALSE.toString()
                        )
        );

        this.cliOptions.add(
                new CliOption(
                        NG_VERSION,
                        "The version of Angular. Default is '4.3'"
                )
        );

        this.cliOptions.add(
                new CliOption(
                        PROVIDED_IN_ROOT,
                        "Use this property to provide Injectables in root (it is only valid in angular version greater or equal to 6.0.0).",
                        SchemaTypeUtil.BOOLEAN_TYPE
                )
                        .defaultValue(
                                Boolean.FALSE.toString()
                        )
        );

        this.cliOptions.add(
                new CliOption(
                        USE_OVERRIDE,
                        "Use this property to place `override` keyword in encoder methods.",
                        SchemaTypeUtil.BOOLEAN_TYPE
                )
                        .defaultValue(
                                Boolean.FALSE.toString()
                        )
        );
    }

    @Override
    protected void addAdditionPropertiesToCodeGenModel(
            final CodegenModel codegenModel,
            final Schema schema
    ) {

        if (schema instanceof MapSchema && hasSchemaProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration((Schema) schema.getAdditionalProperties());
            addImport(codegenModel, codegenModel.additionalPropertiesType);
            return;
        }

        if (schema instanceof MapSchema && hasTrueAdditionalProperties(schema)) {
            codegenModel.additionalPropertiesType = getTypeDeclaration(new ObjectSchema());
        }
    }

    @Override
    public String getName() {
        return "typescript-angular";
    }

    @Override
    public String getHelp() {
        return "Generates a TypeScript Angular (2.x or 4.x) client library.";
    }

    @Override
    public void processOpts() {
        super.processOpts();

        modelTemplateFiles.put("model.mustache", ".ts");
        apiTemplateFiles.put("api.service.mustache", ".ts");

        languageSpecificPrimitives.add("Blob");
        typeMapping.put("file", "Blob");
        apiPackage = "api";
        modelPackage = "model";

        configureSupportFiles();

        SemVer ngVersion = determineNgVersion();

        additionalProperties.put(NG_VERSION, ngVersion);

        // for Angular 2 AOT support we will use good-old ngc,
        // Angular Package format wasn't invented at this time and building was much more easier
        additionalProperties.put(NG_PACKAGR, true);

        if (!ngVersion.atLeast("4.0.0")) {
            logger.warn("Please update your legacy Angular " + ngVersion + " project to benefit from 'Angular Package Format' support.");
            additionalProperties.put(NG_PACKAGR, false);
        }

        // Set the rxJS version compatible to the Angular version
        if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("rxjsVersion", "6.5.0");
            additionalProperties.put("useRxJS6", true);
        } else if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("rxjsVersion", "6.3.0");
            additionalProperties.put("useRxJS6", true);
        } else if (ngVersion.atLeast("6.0.0")) {
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("useRxJS6", true);
        } else {
            // Angular prior to v6
            additionalProperties.put("rxjsVersion", "5.4.0");
        }

        if (!ngVersion.atLeast("4.3.0")) {
            supportingFiles.add(
                    new SupportingFile(
                            "rxjs-operators.mustache",
                            getIndexDirectory(),
                            "rxjs-operators.ts"
                    )
            );
        }

        // Version after Angular 10 require ModuleWithProviders to be generic. Compatible from version 7.
        if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("genericModuleWithProviders", true);
        }

        // for Angular 2 AOT support we will use good-old ngc,
        // Angular Package format wasn't invented at this time and building was much more easier
        if (!ngVersion.atLeast("4.0.0")) {
            logger.warn(
                    "Please update your legacy Angular " + ngVersion + " project to benefit from 'Angular Package Format' support."
            );

            additionalProperties.put("useNgPackagr", false);
        } else {
            additionalProperties.put("useNgPackagr", true);
            supportingFiles.add(
                    new SupportingFile(
                            "ng-package.mustache",
                            getIndexDirectory(),
                            "ng-package.json"
                    )
            );
        }

        // Libraries generated with v1.x of ng-packagr will ship with AoT metadata in v3, which is intended for Angular v4.
        // Libraries generated with v2.x of ng-packagr will ship with AoT metadata in v4, which is intended for Angular v5 (and Angular v6).
        additionalProperties.put("useOldNgPackagr", !ngVersion.atLeast("5.0.0"));

        // set http client usage
        if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("useHttpClient", true);
        } else if (ngVersion.atLeast("4.3.0")) {
            additionalProperties.put("useHttpClient", true);
        } else {
            additionalProperties.put("useHttpClient", false);
        }

        if (additionalProperties.containsKey(PROVIDED_IN_ROOT) && !ngVersion.atLeast("6.0.0")) {
            additionalProperties.put(PROVIDED_IN_ROOT, false);
        }

        additionalProperties.put("injectionToken", ngVersion.atLeast("4.0.0") ? "InjectionToken" : "OpaqueToken");
        additionalProperties.put("injectionTokenTyped", ngVersion.atLeast("4.0.0"));

        if (additionalProperties.containsKey(NPM_NAME)) {
            addNpmPackageGeneration(ngVersion);
        }

        if (additionalProperties.containsKey(WITH_INTERFACES)) {
            boolean withInterfaces = Boolean.parseBoolean(additionalProperties.get(WITH_INTERFACES).toString());
            if (withInterfaces) {
                apiTemplateFiles.put("apiInterface.mustache", "Interface.ts");
            }
        }

        if (additionalProperties.containsKey(USE_OVERRIDE)) {
            final boolean useOverride = Boolean.parseBoolean(String.valueOf(additionalProperties.get(USE_OVERRIDE)));
            additionalProperties.put(USE_OVERRIDE, useOverride);
        }

        kebabFileNaming = Boolean.parseBoolean(String.valueOf(additionalProperties.get(KEBAB_FILE_NAME)));

    }

    private void configureSupportFiles() {
        supportingFiles.add(
                new SupportingFile(
                        "models.mustache",
                        modelPackage().replace('.', '/'),
                        "models.ts"
                )
        );
        supportingFiles
                .add(
                        new SupportingFile(
                                "apis.mustache",
                                apiPackage().replace('.', '/'),
                                "api.ts"
                        )
                );

        supportingFiles.add(
                new SupportingFile(
                        "index.mustache",
                        getIndexDirectory(),
                        "index.ts"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "api.module.mustache",
                        getIndexDirectory(),
                        "api.module.ts"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "configuration.mustache",
                        getIndexDirectory(),
                        "configuration.ts"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "variables.mustache",
                        getIndexDirectory(),
                        "variables.ts"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "encoder.mustache",
                        getIndexDirectory(),
                        "encoder.ts"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "gitignore",
                        "",
                        ".gitignore"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "npmignore",
                        "",
                        ".npmignore"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "git_push.sh.mustache",
                        "",
                        "git_push.sh"
                )
        );
    }

    private SemVer determineNgVersion() {
        SemVer ngVersion;
        if (additionalProperties.containsKey(NG_VERSION)) {
            ngVersion = new SemVer(additionalProperties.get(NG_VERSION).toString());
        } else {
            ngVersion = new SemVer("8.0.0");
            logger.info("generating code for Angular {} ...", ngVersion);
            logger.info("  (you can select the angular version by setting the additionalProperty ngVersion)");
        }
        return ngVersion;
    }

    private void addNpmPackageGeneration(
            final SemVer ngVersion
    ) {
        if (additionalProperties.containsKey(NPM_NAME)) {
            this.setNpmName(additionalProperties.get(NPM_NAME).toString());
        }

        if (additionalProperties.containsKey(NPM_VERSION)) {
            this.setNpmVersion(additionalProperties.get(NPM_VERSION).toString());
        }

        if (
                additionalProperties.containsKey(SNAPSHOT)
                        && Boolean.parseBoolean(additionalProperties.get(SNAPSHOT).toString())
        ) {
            this.setNpmVersion(npmVersion + "-SNAPSHOT." + SNAPSHOT_SUFFIX_FORMAT.format(new Date()));
        }
        additionalProperties.put(NPM_VERSION, npmVersion);

        if (additionalProperties.containsKey(NPM_REPOSITORY)) {
            this.setNpmRepository(additionalProperties.get(NPM_REPOSITORY).toString());
        }

        additionalProperties.put("useHttpClientPackage", false);
        if (ngVersion.atLeast("15.0.0")) {
            additionalProperties.put("tsVersion", ">=4.8.2 <4.10.0");
            additionalProperties.put("rxjsVersion", "7.5.5");
            additionalProperties.put("ngPackagrVersion", "15.0.2");
            additionalProperties.put("zonejsVersion", "0.11.5");
        } else if (ngVersion.atLeast("14.0.0")) {
            additionalProperties.put("tsVersion", ">=4.6.0 <=4.8.0");
            additionalProperties.put("rxjsVersion", "7.5.5");
            additionalProperties.put("ngPackagrVersion", "14.0.2");
            additionalProperties.put("zonejsVersion", "0.11.5");
        } else if (ngVersion.atLeast("13.0.0")) {
            additionalProperties.put("tsVersion", ">=4.4.2 <4.5.0");
            additionalProperties.put("rxjsVersion", "7.4.0");
            additionalProperties.put("ngPackagrVersion", "13.0.3");
            additionalProperties.put("zonejsVersion", "0.11.4");
        } else if (ngVersion.atLeast("12.0.0")) {
            additionalProperties.put("tsVersion", ">=4.3.0 <4.4.0");
            additionalProperties.put("rxjsVersion", "7.4.0");
            additionalProperties.put("ngPackagrVersion", "12.2.1");
            additionalProperties.put("zonejsVersion", "0.11.4");
        } else if (ngVersion.atLeast("11.0.0")) {
            additionalProperties.put("tsVersion", ">=4.0.0 <4.1.0");
            additionalProperties.put("rxjsVersion", "6.6.0");
            additionalProperties.put("ngPackagrVersion", "11.0.2");
            additionalProperties.put("zonejsVersion", "0.11.3");
        } else if (ngVersion.atLeast("10.0.0")) {
            additionalProperties.put("tsVersion", ">=3.9.2 <4.0.0");
            additionalProperties.put("rxjsVersion", "6.6.0");
            additionalProperties.put("ngPackagrVersion", "10.0.3");
            additionalProperties.put("zonejsVersion", "0.10.2");
        } else if (ngVersion.atLeast("9.0.0")) {
            additionalProperties.put("tsVersion", ">=3.6.0 <3.8.0");
            additionalProperties.put("rxjsVersion", "6.5.3");
            additionalProperties.put("ngPackagrVersion", "9.0.1");
            additionalProperties.put("zonejsVersion", "0.10.2");
        } else if (ngVersion.atLeast("8.0.0")) {
            additionalProperties.put("tsVersion", ">=3.4.0 <3.6.0");
            additionalProperties.put("rxjsVersion", "6.5.0");
            additionalProperties.put("ngPackagrVersion", "5.4.0");
            additionalProperties.put("zonejsVersion", "0.9.1");
        } else if (ngVersion.atLeast("7.0.0")) {
            additionalProperties.put("tsVersion", ">=3.1.1 <3.2.0");
            additionalProperties.put("rxjsVersion", "6.3.0");
            additionalProperties.put("ngPackagrVersion", "5.1.0");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        } else if (ngVersion.atLeast("6.0.0")) {
            additionalProperties.put("tsVersion", ">=2.7.2 and <2.10.0");
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("ngPackagrVersion", "3.0.6");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        } else {
            additionalProperties.put("tsVersion", ">=2.1.5 and <2.8");
            additionalProperties.put("rxjsVersion", "6.1.0");
            additionalProperties.put("ngPackagrVersion", "3.0.6");
            additionalProperties.put("zonejsVersion", "0.8.26");

            additionalProperties.put("useHttpClientPackage", true);
        }

        //Files for building our lib
        supportingFiles.add(
                new SupportingFile(
                        "README.mustache",
                        getIndexDirectory(),
                        "README.md"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "package.mustache",
                        getIndexDirectory(),
                        "package.json"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "typings.mustache",
                        getIndexDirectory(),
                        "typings.json"
                )
        );

        supportingFiles.add(
                new SupportingFile(
                        "tsconfig.mustache",
                        getIndexDirectory(),
                        "tsconfig.json"
                )
        );

        if (
                additionalProperties.containsKey(NG_PACKAGR)
                        && Boolean.valueOf(additionalProperties.get(NG_PACKAGR).toString())
        ) {
            supportingFiles.add(
                    new SupportingFile(
                            "ng-package.mustache",
                            getIndexDirectory(),
                            "ng-package.json"
                    )
            );
        }
    }

    private String getIndexDirectory() {
        String indexPackage = modelPackage.substring(0, Math.max(0, modelPackage.lastIndexOf('.')));
        return indexPackage.replace('.', File.separatorChar);
    }

    @Override
    public boolean isDataTypeFile(final String dataType) {
        return dataType != null && dataType.equals("Blob");
    }

    @Override
    public String getArgumentsLocation() {
        return null;
    }

    @Override
    public String getDefaultTemplateDir() {
        return "custom-typescript-angular";
    }

    @Override
    public String getTypeDeclaration(final Schema propertySchema) {
        Schema inner;

        if (propertySchema instanceof ArraySchema) {
            ArraySchema arraySchema = (ArraySchema) propertySchema;
            inner = arraySchema.getItems();
            return this.getSchemaType(propertySchema) + "<" + this.getTypeDeclaration(inner) + ">";
        }

        if (propertySchema instanceof MapSchema && hasSchemaProperties(propertySchema)) {
            inner = (Schema) propertySchema.getAdditionalProperties();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        }

        if (propertySchema instanceof MapSchema && hasTrueAdditionalProperties(propertySchema)) {
            inner = new ObjectSchema();
            return "{ [key: string]: " + this.getTypeDeclaration(inner) + "; }";
        }

        if (propertySchema instanceof FileSchema || propertySchema instanceof BinarySchema) {
            return "Blob";
        }

        if (propertySchema instanceof ObjectSchema) {
            return "any";
        }

        return super.getTypeDeclaration(propertySchema);
    }

    @Override
    public String getSchemaType(
            final Schema schema
    ) {
        String swaggerType = super.getSchemaType(schema);

        if (
                isLanguagePrimitive(swaggerType)
                        || isLanguageGenericType(swaggerType)
        ) {
            return swaggerType;
        }

        applyLocalTypeMapping(swaggerType);
        return swaggerType;
    }

    private String applyLocalTypeMapping(
            final String type
    ) {

        if (typeMapping.containsKey(type)) {
            return typeMapping.get(type);
        }

        return type;
    }

    private boolean isLanguagePrimitive(final String type) {
        return languageSpecificPrimitives.contains(type);
    }

    private boolean isLanguageGenericType(String type) {
        for (String genericType : languageGenericTypes) {
            if (type.startsWith(genericType + "<")) {
                return true;
            }
        }
        return false;
    }

    protected void addOperationImports(CodegenOperation codegenOperation, Set<String> operationImports) {
        for (String operationImport : operationImports) {
            if (operationImport.contains("|")) {
                String[] importNames = operationImport.split("\\|");
                for (String importName : importNames) {
                    importName = importName.trim();
                    if (needToImport(importName)) {
                        codegenOperation.imports.add(importName);
                    }
                }
            } else {
                if (needToImport(operationImport)) {
                    codegenOperation.imports.add(operationImport);
                }
            }
        }
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter) {
        super.postProcessParameter(parameter);
        parameter.dataType = applyLocalTypeMapping(parameter.dataType);
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> operations) {
        Map<String, Object> objs = (Map<String, Object>) operations.get("operations");

        // Add filename information for api imports
        objs.put("apiFilename", getApiFilenameFromClassname(objs.get("classname").toString()));

        List<CodegenOperation> ops = (List<CodegenOperation>) objs.get("operation");
        for (CodegenOperation op : ops) {
            if ((boolean) additionalProperties.get("useHttpClient")) {
                op.httpMethod = op.httpMethod.toLowerCase(Locale.ENGLISH);
            } else {
                // Convert httpMethod to Angular's RequestMethod enum
                // https://angular.io/docs/ts/latest/api/http/index/RequestMethod-enum.html
                op.httpMethod = extractHttpMethod(op);
            }

            // Prep a string buffer where we're going to set up our new version of the string.
            StringBuilder pathBuffer = new StringBuilder();
            StringBuilder parameterName = new StringBuilder();
            int insideCurly = 0;

            // Iterate through existing string, one character at a time.
            for (int i = 0; i < op.path.length(); i++) {
                switch (op.path.charAt(i)) {
                    case '{':
                        // We entered curly braces, so track that.
                        insideCurly++;

                        // Add the more complicated component instead of just the brace.
                        pathBuffer.append("${encodeURIComponent(String(");
                        break;
                    case '}':
                        // We exited curly braces, so track that.
                        insideCurly--;

                        // Add the more complicated component instead of just the brace.
                        pathBuffer.append(toVarName(parameterName.toString()));
                        pathBuffer.append("))}");
                        parameterName.setLength(0);
                        break;
                    default:
                        if (insideCurly > 0) {
                            parameterName.append(op.path.charAt(i));
                        } else {
                            pathBuffer.append(op.path.charAt(i));
                        }
                        break;
                }
            }

            // Overwrite path to TypeScript template string, after applying everything we just did.
            op.path = pathBuffer.toString();
        }

        // Add additional filename information for model imports in the services
        List<Map<String, Object>> imports = (List<Map<String, Object>>) operations.get("imports");
        for (Map<String, Object> im : imports) {
            im.put("filename", im.get("import"));
            im.put("classname", getModelnameFromModelFilename(im.get("filename").toString()));
        }

        return operations;
    }

    private String extractHttpMethod(
            final CodegenOperation op
    ) {


        final String httpMethod = op.httpMethod.toUpperCase(Locale.ROOT);

        if (!validHttpMethods.contains(httpMethod)) {
            throw new RuntimeException("Unknown HTTP Method " + httpMethod + " not allowed");
        }

        return "RequestMethod." + httpMethod.substring(0, 1) + httpMethod.substring(1).toLowerCase(Locale.ROOT);

    }

    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        Map<String, Object> result = super.postProcessModels(objs);

        // Add additional filename information for imports
        List<Object> models = (List<Object>) postProcessModelsEnum(result).get("models");
        for (Object _mo : models) {
            Map<String, Object> mo = (Map<String, Object>) _mo;
            CodegenModel cm = (CodegenModel) mo.get("model");
            mo.put("tsImports", toTsImports(cm, cm.imports));
        }

        return result;
    }

    @Override
    public Map<String, Object> postProcessAllModels(Map<String, Object> processedModels) {
        for (Map.Entry<String, Object> entry : processedModels.entrySet()) {
            final Map<String, Object> inner = (Map<String, Object>) entry.getValue();
            final List<Map<String, Object>> models = (List<Map<String, Object>>) inner.get("models");
            for (Map<String, Object> mo : models) {
                final CodegenModel codegenModel = (CodegenModel) mo.get("model");
                if (codegenModel.getIsAlias() && codegenModel.imports != null && !codegenModel.imports.isEmpty()) {
                    mo.put("tsImports", toTsImports(codegenModel, codegenModel.imports));
                }
            }
        }
        return processedModels;
    }

    private List<Map<String, String>> toTsImports(CodegenModel cm, Set<String> imports) {
        List<Map<String, String>> tsImports = new ArrayList<>();
        for (String im : imports) {
            if (!im.equals(cm.classname)) {
                HashMap<String, String> tsImport = new HashMap<>();
                tsImport.put("classname", im);
                tsImport.put("filename", toModelFilename(im));
                tsImports.add(tsImport);
            }
        }
        return tsImports;
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultService";
        }
        return initialCaps(name) + "Service";
    }

    @Override
    public String toApiFilename(String name) {
        if (name.length() == 0) {
            return "default.service";
        }
        if (kebabFileNaming) {
            return dashize(name);
        }
        return camelize(name, true) + ".service";
    }

    @Override
    public String toApiImport(String name) {
        return apiPackage() + "/" + toApiFilename(name);
    }

    @Override
    public String toModelFilename(String name) {
        if (kebabFileNaming) {
            return dashize(name);
        }
        return camelize(toModelName(name), true);
    }

    @Override
    public String toModelImport(final String name) {
        return modelPackage() + "/" + toModelFilename(name);
    }

    public String getNpmName() {
        return npmName;
    }

    public void setNpmName(
            final String npmName
    ) {
        this.npmName = npmName;
    }

    public String getNpmVersion() {
        return npmVersion;
    }

    public void setNpmVersion(
            final String npmVersion
    ) {
        this.npmVersion = npmVersion;
    }

    public String getNpmRepository() {
        return npmRepository;
    }

    public void setNpmRepository(
            final String npmRepository
    ) {
        this.npmRepository = npmRepository;
    }

    private String getApiFilenameFromClassname(
            final String classname
    ) {
        String name = classname.substring(0, classname.length() - "Service".length());
        return toApiFilename(name);
    }

    private String getModelnameFromModelFilename(
            final String filename
    ) {
        String name = filename.substring((modelPackage() + File.separator).length());
        return camelize(name);
    }


}