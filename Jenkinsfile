def rastaResultsDir = ''
node('apjc-sio-slv01') {
  stage('SCM Checkout') {
    checkout scm
  }
  stage('Install Dependencies') {
    sh "${WORKSPACE}/scripts/install_dependecies.sh"
  }
  stage('Install NSO') {
    sh "${WORKSPACE}/scripts/install_nso.sh"
  }
  stage('Build and Deploy') {
    sh "${WORKSPACE}/scripts/build.sh"
  }
  stage('Run RASTA Tests') {
    rastaResultsDir = "${WORKSPACE}/rasta/tests/check-dc-vlan-service"
    echo "Rasta results dir ${rastaResultsDir}"
    sh "${WORKSPACE}/scripts/run_rasta_tests.sh"
  }
  stage('Cleanup') {
    sh "${WORKSPACE}/scripts/cleanup.sh"
  }
  stage('Collect Tests Results') {
    step([$class : 'RobotPublisher', outputPath : "${rastaResultsDir}", disableArchiveOutput : false, passThreshold : 100.0, unstableThreshold: 60.0, otherFiles: ""])
  }
}
