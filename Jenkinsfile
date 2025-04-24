@Library('jenkins-sharedlib@develop-integration/node-library')
import sharedlib.WebAngularMicroAppUtil

def utils = new WebAngularMicroAppUtil(steps, this);
def recipients = '';
def project = 'INCT';
def deploymentEnvironment = 'dev';
def storageAccountName = "stgeu2nfmcd03";

try {
   node {
    stage('Preparation') {
      utils.notifyByMail('START', recipients);
      checkout scm;
      env.project = "${project}";
      env.deploymentEnvironment = "${deploymentEnvironment}";
      utils.setNodeVersion("NODE10_ANGULAR7_CHROME78");
      utils.setReportPathsForSonar("coverage/microapp");
      utils.enableJscrambler();
      utils.prepare();
      utils.setAzureKeyVaultEnabled(false);
      utils.setAngularEnvironmentFromVault(["inct-key-encription-desa":"encrypt_value", "inct-azure-api-url":"url_api"],"src/environments/environment.dev.ts");
      utils.setReuseJscramblerApp("ob");
    }

    stage('Build') { 
      utils.build();
    }

    stage('QA') { 
      utils.executeQA();
    }

    stage('SAST Analisys') {
      utils.executeSast();
    }

    stage('Deploy Artifact') {
      utils.uploadArtifact();
    }

     stage('Deploy Azure') {
       //def APP_NAME = utils.getApplicationName();
       //def APP_VERSION = utils.getApplicationVersion();
       //def AZURE_RESOURCE_GROUP_NAME = "";
       //def AZURE_WEBAPP_NAME = "";

       //utils.withAzureVaultCredentials([
       //[azureCredentialId: "api-gateway-client-id", azureCredentialVariable: "apiGatewayClientId" ],
       //[azureCredentialId: "API-BCP-URL", azureCredentialVariable: "apiGatewayClientSecret" ],
       //]) {
       //utils.setWebAppSettingsEnvironmentVariables([
       //"API_GATEWAY_CLIENT_ID=${env.apiGatewayClientId}",
       //"API_GATEWAY_CLIENT_SECRET=${env.apiGatewayClientSecret}",
       //"API_BCP_URL=https://bcp-node-api-mock.azurewebsites.net"
       //]);
       //utils.deployToAzureWebappContainer(APP_NAME,APP_VERSION,AZURE_RESOURCE_GROUP_NAME,AZURE_WEBAPP_NAME,true);
     }
    
    stage('Results') {
      utils.saveResult("tgz");
    }

    stage('Post Execution') {
      utils.executePostExecutionTasks();
      utils.notifyByMail('SUCCESS', recipients);
    }
  }
} catch(Exception e) {
  node {
    utils.executeOnErrorExecutionTasks();
    throw e;
  }
}