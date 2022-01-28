//
//  Author: Hari Sekhon
//  Date: 2021-04-30 15:25:01 +0100 (Fri, 30 Apr 2021)
//
//  vim:ts=2:sts=2:sw=2:et
//
//  https://github.com/HariSekhon/Jenkins
//
//  License: see accompanying Hari Sekhon LICENSE file
//
//  If you're using my code you're welcome to connect with me on LinkedIn and optionally send me feedback to help steer this or other code I publish
//
//  https://www.linkedin.com/in/HariSekhon
//

def call(){
  echo "Building from branch '${env.GIT_BRANCH}' for '" + "${env.ENVIRONMENT}".capitalize() + "' Environment"
  milestone ordinal: 10, label: "Milestone: Build"
  echo "Running Job '${env.JOB_NAME}' Build ${env.BUILD_ID} on ${env.JENKINS_URL}"
  timeout(time: 1, unit: 'MINUTES') {
    sh script: 'env | sort', label: 'Environment'
  }
  retry(2){
    timeout(time: 40, unit: 'MINUTES') {
      // script from DevOps Bash tools repo
      // external script needs to exist in the source repo, not the shared library repo
      sh 'gcp_ci_build.sh'
    }
  }
}
