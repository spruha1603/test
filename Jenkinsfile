def rastaResultsDir = ''
node('localhost') {
  stage('SCM Checkout') {
    checkout scm
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
