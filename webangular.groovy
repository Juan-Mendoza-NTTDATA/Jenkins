package sharedlib

import groovy.json.JsonOutput;
import sharedlib.NodeLibraryJenkinsUtil;
import sharedlib.utils.AzureUtil;
import sharedlib.security.vault.HashicorpVault;
import sharedlib.helper.CredentialHelper;
import sharedlib.SecurityJenkinsUtil;


class WebAngularMicroAppUtil extends NodeLibraryJenkinsUtil {
  private node10Angular7Version                   = "NODE10_ANGULAR7"
  private node10Angular7Chrome78Version           = "NODE10_ANGULAR7_CHROME78"
  private node12Version					                  = "NODE12"
  private node12Chrome78Version					          = "NODE12_CHROME78"
  private node12Chrome85Version					          = "NODE12_CHROME85"
  private node16Chrome97Version					          = "NODE16_CHROME97"
  private node18Chrome113Version					        = "NODE18_CHROME113"
  private dockerNode10Angular7Enviroment          = "bcp/node10-angular7:1.1"
  private dockerNode10Angular7Chrome78Enviroment  = "bcp/node10-angular7-chrome78:1.0"
  private dockerNode12Enviroment		 		          = "bcp/node12:1.0"
  private dockerNode12Chrome78Enviroment    	    = "bcp/node12-chrome78:2.0"
  private dockerNode12Chrome85Enviroment    	    = "bcp/node12-chrome85:2.0"
  private dockerNode16Chrome97Enviroment    	    = "bcp/node16-chrome97:1.0"
  private dockerNode18Chrome113Enviroment    	    = "bcp/node18-chrome113:1.0"
  private boolean isEnableJscrambler              = false
  String  reuseJscramblerApp                      = ""
  private AzureUtil azureUtil
  private Map credentialsMap = [:]
  private HashicorpVault vault
  private boolean jenkinsVaultActivated = true
  private boolean hashicorpVaultActivated = false
  private String hashicorpVaultNamespace = "devsecops"
  private String hashicorpVaultEnvironment = ""
  private boolean isEnableJscramblerSaaS          = false
  private String jscramblerSaasApiUrl = "imu30977-82.jscrambler.com"
  private String jscramblerSaasApiVersion = "8.2"

  WebAngularMicroAppUtil(steps, script, String type = '') {
    super(steps, script, type);

    this.azureUtil = new AzureUtil(script, steps);
    this.vault = new HashicorpVault(this.script)
  }
  
  private void setCustomCredentials(Map credentials){
  	credentialsMap = credentials
  }

  public void enableJscrambler() {
    this.isEnableJscrambler = true;
  }

  public void enableJscramblerSaaS() {
    this.isEnableJscramblerSaaS = true;
  }

  public void setConfigJscrambler(String jscramblerApiUrl, String jscramblerApiVersion) {
    this.jscramblerSaasApiUrl = jscramblerApiUrl;
    this.jscramblerSaasApiVersion = jscramblerApiVersion;
  }

  public void setNodeVersion(String nodeVersion) {
    if ("${node10Angular7Version}"=="${nodeVersion}") {
      dockerNodeEnviroment = this.dockerNode10Angular7Enviroment
    }
    else if("${node10Angular7Chrome78Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode10Angular7Chrome78Enviroment
    }
    else if ("${node12Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode12Enviroment
    }
    else if ("${node12Chrome78Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode12Chrome78Enviroment
    }
    else if ("${node12Chrome85Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode12Chrome85Enviroment
    }
    else if ("${node16Chrome97Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode16Chrome97Enviroment
    }
    else if ("${node18Chrome113Version}"=="${nodeVersion}"){
      dockerNodeEnviroment = this.dockerNode18Chrome113Enviroment
    }
    else{
      throw new Exception("Not supported version: "+nodeVersion)
    }
    steps.echo "******** NODE VERSION IS SET TO: "+nodeVersion+" ********"
    steps.echo "******** ENVIROMENT SET TO: "+dockerNodeEnviroment+" ********"
  }

  public void setWebAngularMicroAppWorkspace(String workspace) {
    setWorkspace(workspace)
  }

  public void build(final String buildParams){
    configureAngularEnvironmentFromVault {
      this.installDependencies()
      if(script.env.BUILD_DEBUG?.toBoolean()) {
        this.executeDockerCmd(["npm run debug"])
      }
      String deploymentEnvironment = "${script.env.deploymentEnvironment}".toLowerCase()
      this.executeDockerCmd(["npm run build:${deploymentEnvironment}"])
      this.configureJscrambler()
    }
  }

  Map jscramblerProperties = [
    onpremise : [
      apiUrl      : "${script.env.JSCRAMBLER_API_HOST}",
      apiPort     : "${script.env.JSCRAMBLER_API_PORT}",
      apiProtocol : "${script.env.JSCRAMBLER_API_PROTOCOL}",
      apiVersion  : "5.4",
      cliVersion  : "5.2.16",
      credentialsPattern : "jscrambler"
    ],
    saas : [
      apiUrl      : "${jscramblerSaasApiUrl}",
      apiPort     : "443",
      apiProtocol : "https",
      apiVersion  : "${jscramblerSaasApiVersion}",
      cliVersion  : "6.2.6",
      credentialsPattern : "jscrambler-saas"
    ]
  ]

  private String getJscramblerConfig(String jscramblerType){

    String jscramblerApiVersion    = jscramblerProperties["${jscramblerType}"].apiVersion
    String jscramblerNpmVersion    = jscramblerProperties["${jscramblerType}"].cliVersion
    String jscramblerProtocol      = jscramblerProperties["${jscramblerType}"].apiProtocol
    String jscramblerPort          = jscramblerProperties["${jscramblerType}"].apiPort
    String jscramblerHost          = jscramblerProperties["${jscramblerType}"].apiUrl

    String config = ""
    if(isEnableJscrambler){
      config = """
          "jscramblerVersion"    : "${jscramblerApiVersion}",
          "protocol"             : "${jscramblerProtocol}",
          "host"                 : "${jscramblerHost}",
          "port"                 : "${jscramblerPort}",
          "useRecommendedOrder"  : true,
          "areSubscribersOrdered": false,
          "sourceMaps"           : false
      """
    }else{
      config = """
          "jscramblerVersion"    : "${jscramblerApiVersion}",
          "protocol"             : "${jscramblerProtocol}",
          "host"                 : "${jscramblerHost}",
          "useRecommendedOrder"  : true,
          "areSubscribersOrdered": false,
          "sourceMaps"           : false,
          "basePath": "/api"
      """
    }
    return config
  }


  private void configureJscrambler(){
    if (!isEnableJscrambler && !isEnableJscramblerSaaS) {
      return
    }

    String projectName = "${script.env.project}".toLowerCase()
    String appName     = getApplicationName().toLowerCase()

    String jscramblerType = isEnableJscrambler ? "onpremise" : "saas"

    String jscramblerConfigFile  = "jscrambler.json"
    String jscramblerTempFile    = "jscrambler-tmp.json"
    String jscramlerFinalFile    = "jscrambler-final.json"
    String mainRulesRepository   = "https://${script.env.GITLAB_HOST}/projects/shared/repos/jscrambler-rules/raw/angular-rules.json?raw&at=3b5181b570dcf594722b6de4f7adc52c9253a03c"
  
    String jscramblerApiVersion         = jscramblerProperties["${jscramblerType}"].apiVersion
    String jscramblerNpmVersion         = jscramblerProperties["${jscramblerType}"].cliVersion
    String jscramblerCredentialsPattern         = jscramblerProperties["${jscramblerType}"].credentialsPattern
    
    String jscramblerApplicationId   = "${projectName}-${appName}-${jscramblerCredentialsPattern}-app-id"
    String jscramblerAccessKey   	   = "${projectName}-${appName}-${jscramblerCredentialsPattern}-access-key"
    String jscramblerSecretKey       = "${projectName}-${appName}-${jscramblerCredentialsPattern}-secret-key"

    if(credentialsMap.containsKey("jscramblerCredentials")){
      jscramblerApplicationId   = credentialsMap.jscramblerCredentials.jscramblerApplicationId
      jscramblerAccessKey 		  = credentialsMap.jscramblerCredentials.jscramblerAccessKey
      jscramblerSecretKey 	    = credentialsMap.jscramblerCredentials.jscramblerSecretKey
    }	

    if(reuseJscramblerApp && reuseJscramblerApp.length()>0){
      jscramblerApplicationId = "${projectName}-${reuseJscramblerApp}-${jscramblerCredentialsPattern}-app-id"
      jscramblerAccessKey     = "${projectName}-${reuseJscramblerApp}-${jscramblerCredentialsPattern}-access-key"
      jscramblerSecretKey     = "${projectName}-${reuseJscramblerApp}-${jscramblerCredentialsPattern}-secret-key"
    }

    steps.echo """

      JSCRAMBLER OBFUSCATION

      NOTE: - The credentials should be populate on project folder credentials
        * Jscrambler Application ID : ${jscramblerApplicationId}
        * Jscrambler Access Key     : ${jscramblerAccessKey}
        * Jscrambler Secret Key     : ${jscramblerSecretKey}

      Jscrambler INFO
        * Jscrambler version: ${jscramblerApiVersion}
        * Jscrambler CLI version: ${jscramblerNpmVersion}
    """

    def existsConfigFileInRepo = steps.fileExists(jscramblerConfigFile);

    if(!existsConfigFileInRepo){
      steps.sh """
        set +x
        curl -s -o ${jscramblerConfigFile} -k \"${mainRulesRepository}\"
      """
    }

    try{
      //INICIO DE REFACTORIZACION
      /*String jscramblerApplicationId = ""
      String jscramblerAccessKey = ""
      String jscramblerSecretKey = ""*/

      if(hashicorpVaultActivated || !jenkinsVaultActivated){
        withHashicorpVaultCredential(
          [[vaultCredentialPath: "${jscramblerApplicationId}",
            vaultCredentialType: HashicorpVault.VAULT_SECRET_TYPE_SECRET_TEXT,
            secret: "jscramblerApplicationIdValue"
           ],
           [vaultCredentialPath: "${jscramblerAccessKey}",
            vaultCredentialType: HashicorpVault.VAULT_SECRET_TYPE_SECRET_TEXT,
            secret: "jscramblerAccessKeyValue"
           ],
           [vaultCredentialPath: "${jscramblerSecretKey}",
            vaultCredentialType: HashicorpVault.VAULT_SECRET_TYPE_SECRET_TEXT,
            secret: "jscramblerSecretKeyValue"
           ]
          ]){
            jscramblerApplicationId = "${script.env.jscramblerApplicationIdValue}"
            jscramblerAccessKey = "${script.env.jscramblerAccessKeyValue}"
            jscramblerSecretKey = "${script.env.jscramblerSecretKeyValue}"
          }
      } else {
        this.script.steps.echo """
          *********************************************************
          ********** HASHICORP VAULT NO ESTA HABILITADO  **********
          *********************************************************"""
        steps.withCredentials([
        [$class: "StringBinding", credentialsId: "${jscramblerApplicationId}", variable: "jscramblerApplicationIdValue" ],
        [$class: "StringBinding", credentialsId: "${jscramblerAccessKey}", variable: "jscramblerAccessKeyValue" ],
        [$class: "StringBinding", credentialsId: "${jscramblerSecretKey}", variable: "jscramblerSecretKeyValue" ]
        ]){
          jscramblerApplicationId = "${script.env.jscramblerApplicationIdValue}"
          jscramblerAccessKey = "${script.env.jscramblerAccessKeyValue}"
          jscramblerSecretKey = "${script.env.jscramblerSecretKeyValue}"
        }        
      }
      //FIN DE REFACTORIZACION

       script.steps.wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
            [password: jscramblerApplicationId, var: 'jscramblerApplicationIdVariable'],
            [password: jscramblerAccessKey, var: 'jscramblerAccessKeyVariable'],
            [password: jscramblerSecretKey, var: 'jscramblerSecretKeyVariable']
       ]]) {

        String connectionConfig = getJscramblerConfig(jscramblerType)

        def configCreds ="""
        {
          "applicationId"        : "${jscramblerApplicationId}",
          "keys": {
          "accessKey": "${jscramblerAccessKey}",
          "secretKey": "${jscramblerSecretKey}"
        },
          ${connectionConfig}
        }
        """.stripIndent()

        steps.writeFile(
          file:"${script.env.WORKSPACE}/${jscramblerTempFile}",
          text: configCreds
        )

        steps.sh """
        set +x
        jq -s '.[0] * .[1]' ${jscramblerTempFile} ${jscramblerConfigFile} > ${jscramlerFinalFile}
        """

        def httpProxy = "${script.env.DOCKER_HTTP_PROXY_URL}"
        def npmArguments = "--no-package-lock --no-audit --quiet --no-progress --prefer-offline --strict-ssl=false --userconfig=./npmrc-jenkins"
        if(httpProxy){
          npmArguments+=" --proxy ${httpProxy} --https-proxy ${httpProxy}"
        }

        this.executeDockerCmd(
          [
            "npm install ${npmArguments} jscrambler@${jscramblerNpmVersion}",
            "./node_modules/.bin/jscrambler -c ${jscramlerFinalFile}"
          ],
          {
            def deploymentEnvironment = "${script.env.deploymentEnvironment}".toLowerCase();
            def npmUserConfig = getNpmConfiguration(deploymentEnvironment)

            this.steps.writeFile(
              file:"${script.env.WORKSPACE}/npmrc-jenkins",
              text: npmUserConfig
            );
          }
        );
      }
    }catch(Exception e){
      throw e;
    }finally{
      
      steps.sh """
        set +x
        shred -fuz ${jscramblerTempFile}
        shred -fuz ${jscramlerFinalFile}
      """

      if(!existsConfigFileInRepo){
        steps.sh """
          set +x
          shred -fuz ${jscramblerConfigFile}
        """
      }
    }
  }
  
  public void purgeAzureCDN(String group,String cdnProfileName, contentPath=[]) {
    String cdnEndPoint = ""
    String paths = "'"
    def listPath = []
    Integer i = 0
    contentPath.each{
          cdnEndPoint = it.key
          printMessage(it.key)
          listPath = it.value
          paths="'"
          i=0
          listPath.each{
            if (i==0){
              paths += "${it.value}"
               i=i+1
      		}else{
      			paths += "' '"+ it.value
               i=i+1
      		}
          }
          paths += "'"
          printMessage("paths : ${paths}")
          this.azureUtil.setBuildTimeStamp(this.buildTimestamp)
    	  this.azureUtil.purgeCDN(group, cdnEndPoint, cdnProfileName, paths);
    }
  }

  public void deployToAssetsStorageAccount(String accountName, String assetsPath="src/assets") {
    def containerName= "\$web";
    def appName=this.getApplicationName().toLowerCase();
    def targetPath = "assets/${appName}";
    def sourcePath = "${this.script.env.WORKSPACE}/${assetsPath}"
	
    //Corrección del nombre del método
    //this.azureUtil.setHashicorpVaultActivated(this.hashicorpVaultActivated)    
    this.azureUtil.setHashicorpVaultEnabled(this.hashicorpVaultActivated)
    this.azureUtil.setHashicorpVaultInstance(this.hashicorpVaultInstance)
    this.azureUtil.setHashicorpVaultNamespace(this.hashicorpVaultNamespace)
    this.azureUtil.setHashicorpVaultEnvironment(this.hashicorpVaultEnvironment)
    this.azureUtil.setBuildTimeStamp(this.buildTimestamp)
    this.azureUtil.uploadStorageAccount(sourcePath, targetPath, accountName, containerName);
  }

  public void deployToMicroAppStorageAccount(String accountName, String containerName = "micro-app", String  sourcePath = "dist", String fileName = "main.js") {
    def targetPath = "${this.getApplicationName().toLowerCase()}"
    sourcePath = "${this.script.env.WORKSPACE}/${sourcePath}"
    
	//Corrección del nombre del método
    //this.azureUtil.setHashicorpVaultActivated(this.hashicorpVaultActivated)
    this.azureUtil.setHashicorpVaultEnabled(this.hashicorpVaultActivated)
    this.azureUtil.setHashicorpVaultInstance(this.hashicorpVaultInstance)
    this.azureUtil.setHashicorpVaultNamespace(this.hashicorpVaultNamespace)
    this.azureUtil.setHashicorpVaultEnvironment(this.hashicorpVaultEnvironment)
    
    this.azureUtil.setBuildTimeStamp(this.buildTimestamp)
    this.azureUtil.uploadStorageAccount(sourcePath, targetPath, accountName, containerName, fileName, false);
  }

  def configureAngularEnvironmentFromVault(Closure body){
    if(angularEnvVault==null || angularEnvVaultPathFile==null){
      body()
      return
    }

    printMessage("CONFIGURACION DE VARIABLES DE ENTORNO-VAULT ANGULAR")

    if(angularEnvVault.size()<1 || angularEnvVaultPathFile.trim().length()<1){
      throw new Error("Debe usar la funcion setAngularEnvironmentFromVault correctamente")
    }

    def credentialsMap = [:]
    def buildTimestamp = getBuildTimestamp()
    printMessage("buildTimestamp: ${buildTimestamp}")
    String envFile = "env-${buildTimestamp}.json"
    String tsFile = "new-environment-${buildTimestamp}.ts"

    angularEnvVault.each {
      String secretKey = "${it.key}".toString();
      String secretValue = "${it.value}".toString();

      //INICIO DE REFACTORIZACION
      String secretTempVar = ""

      if(hashicorpVaultActivated || !jenkinsVaultActivated){
        withHashicorpVaultCredential(
          [[vaultCredentialPath: "${secretKey}",
            vaultCredentialType: HashicorpVault.VAULT_SECRET_TYPE_SECRET_TEXT,
            secret: "secretTempVarValue"
           ]
          ]){
            secretTempVar = "${script.env.secretTempVarValue}"
          }
      } else {
        this.script.steps.echo """
          *********************************************************
          ********** HASHICORP VAULT NO ESTA HABILITADO  **********
          *********************************************************"""
        steps.withCredentials([
        [$class: "StringBinding", credentialsId: "${secretKey}", variable: "secretTempVarValue" ]
        ]){
          secretTempVar = "${script.env.secretTempVarValue}"
        }        
      }

      script.steps.wrap([$class: 'MaskPasswordsBuildWrapper', varPasswordPairs: [
            [password: secretTempVar, var: 'secretTempVarVariable']
      ]]) {
        credentialsMap["${secretValue}"] = "${secretTempVar}".toString()
      }
      //FIN DE REFACTORIZACION
    }

    String jsonResultParsed = JsonOutput.toJson(credentialsMap)
    //printMessage("jsonResultParsed: ${jsonResultParsed}")

    steps.writeFile(
      file: envFile,
      text: jsonResultParsed.toString()
    )

    String bcpVirtualRegistry = getUrlRepository("${script.env.deploymentEnvironment}".toLowerCase())
    def npmArguments = """"
        registry = ${artifact_npm_registry_url}
        @bcp:registry = ${bcpVirtualRegistry}
        quiet = true
        strict-ssl = false
        progress = false
      """.stripIndent()

    steps.writeFile(
      text: npmArguments,
      file: "${script.env.WORKSPACE}/.npmrc"
    )

    String angularEnvVaultPathFileTmp = angularEnvVaultPathFile.replace(".ts", "")
    String tmpScriptFile = "vaultscript-${buildTimestamp}.ts"

    printMessage("tmpScriptFile: ${tmpScriptFile}")

    def vaultScript = """
      import {environment} from './${angularEnvVaultPathFileTmp}'
      let data = require('./${envFile}');

      let result = {...environment,...data};

      console.log("export const environment =",result);
    """.stripIndent()

    steps.writeFile(
      file:tmpScriptFile,
      text:vaultScript
    )

    executeDockerCmd(
      [
        "npm install --no-save --no-package-lock ts-node typescript @types/node",
        "./node_modules/.bin/ts-node --skip-project ${tmpScriptFile} > ${tsFile}"
      ]
    )

    steps.sh """
      set -x
      mv ${tsFile} ${angularEnvVaultPathFile}
    """

    steps.sh """
      set -x
      shred -fuz ${envFile}
      shred -fuz ${tmpScriptFile}
      shred -fuz ${script.env.WORKSPACE}/.npmrc
    """

    body()

    steps.sh "git checkout ${angularEnvVaultPathFile}"
  }

  protected void buildInfo(String deployPackage, String buildUniqueId = '', String repositoryId){
    def projectName = "${script.env.project}"
    def projectNameLowercase = projectName.toLowerCase()
    def artifactoryUserName
    def artifactoryPassword

    //INICIO DE REFACTORIZACION
    CredentialHelper credentialHelper = new CredentialHelper(this.script)
    credentialHelper.setHashicorpVaultInstance(hashicorpVaultInstance);
    credentialHelper.getArtifactoryCredentials(jenkinsVaultActivated, hashicorpVaultActivated, hashicorpVaultNamespace, hashicorpVaultEnvironment, projectNameLowercase) {
      artifactoryUserName="${script.env.ARTIFACT_USER_NAME}"
      artifactoryPassword="${script.env.ARTIFACT_USER_PASSWORD}"
      putToArtifactory(repositoryId, deployPackage, artifactoryUserName, artifactoryPassword, buildUniqueId)
    }
    //FIN DE REFACTORIZACION
  }
  
  @Override
  void promoteRelease(final String releaseTagName, final boolean forceRelease=false){
    this.printMessage("PROMOTE RELEASE MICROAPP");
    if(!"${this.branchName}".startsWith("tags/RC-")){
        this.throwException("The release must be done on release")
    }

    def applicationVersion = getApplicationVersion()
    def existTag = existTag("${applicationVersion}")
    def tagStateComment = "Release"
    def tagName = getApplicationVersion()

    if(existTag){
        if (forceRelease){
          this.printMessage("Forzando la promocion del nombre de etiquete del relesase: ${releaseTagName}");
          deleteTagBranch(tagName)
          this.printMessage("Tag eliminado: ${releaseTagName}")
          this.printMessage("${tagStateComment}-FORCED")
        } else{
        this.throwException("The tag for this release exists: ${applicationVersion}")
        }
    }

    this.printMessage("Promocion nombre de entrega: ${releaseTagName}");
    def applicationName = getApplicationName()
    //this.verifyTagInArtifactory(releaseTagName)
    this.printMessage("Upload artifact a repositorio de entrega: ${releaseTagName}")
    uploadArtifact(branchName)
    this.script.steps.sh """
          git checkout -f
        """
    mergeOverwriteBranch(releaseTagName,"master")
    tagBranch(tagName,tagStateComment)
  }
  
}