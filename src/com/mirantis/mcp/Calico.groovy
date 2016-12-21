package com.mirantis.mcp

/**
 * Build Calico containers
 *
 * @param body Closure
 *        body includes next parameters:
 *          - dockerRepo String, repo with docker images
 *          - artifactoryUrl String, URL to repo with calico-binaries
 *          - imageTag String, tag of images
 *          - nodeImage String, Calico Node image name
 *          - ctlImage String, Calico CTL image name
 *          - buildImage String, Calico Build image name
 *          - felixImage String, Calico Felix image name
 *          - confdBuildId String, Version of Calico Confd
 *          - confdUrl String, URL to Calico Confd
 *          - birdUrl, URL to Calico Bird
 *          - birdBuildId, Version of Calico Bird
 *          - bird6Url, URL to Calico Bird6
 *          - birdclUrl, URL to Calico BirdCL
 *
 * Usage example:
 *
 * def calicoFunc = new com.mirantis.mcp.Calico()
 * calicoFunc.buildCalicoContainers {
 *     dockerRepo = 'mcp-k8s-ci.docker.mirantis.net'
 *     artifactoryURL = 'https://artifactory.mcp.mirantis.net/artifactory/sandbox'
 *     nodeImage = 'mcp-k8s-ci.docker.mirantis.net/calico/node'
 *     ctlImage = 'mcp-k8s-ci.docker.mirantis.net/calico/ctl'
 * }
 *
 */
def buildCalicoContainers = { body ->
  // evaluate the body block, and collect configuration into the object
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  body()


  def dockerRepo = config.dockerRepo
  def projectNamespace = "mirantis/projectcalico"
  def artifactoryUrl = config.artifactoryURL

  if (! dockerRepo ) {
      error('dockerRepo parameter have to be set.')
  }

  if (! artifactoryUrl ) {
      error('artifactoryUrl parameter have to be set.')
  }

  def imgTag = config.imageTag ?: getGitDescribe(true) + "-" + getDatetime()

  def nodeImage = config.nodeImage ?: "${dockerRepo}/${projectNamespace}/calico/node"
  def nodeName = "${nodeImage}:${imgTag}"

  def ctlImage = config.ctlImage ?: "${dockerRepo}/${projectNamespace}/calico/ctl"
  def ctlName = "${ctlImage}:${imgTag}"

   // calico/build goes from libcalico
  def buildImage = config.buildImage ?: "${dockerRepo}/${projectNamespace}/calico/build:latest"
  // calico/felix goes from felix
  def felixImage = config.felixImage ?: "${dockerRepo}/${projectNamespace}/calico/felix:latest"

  def confdBuildId = config.confdBuildId ?: "${artifactoryUrl}/${projectNamespace}/confd/latest".toURL().text.trim()
  def confdUrl = config.confdUrl ?: "${artifactoryUrl}/${projectNamespace}/confd/confd-${confdBuildId}"

  def birdBuildId = config.birdBuildId ?: "${artifactoryUrl}/${projectNamespace}/bird/latest".toURL().text.trim()
  def birdUrl = config.birdUrl ?: "${artifactoryUrl}/${projectNamespace}/bird/bird-${birdBuildId}"
  def bird6Url = config.bird6Url ?: "${artifactoryUrl}/${projectNamespace}/bird/bird6-${birdBuildId}"
  def birdclUrl = config.birdclUrl ?: "${artifactoryUrl}/${projectNamespace}/bird/birdcl-${birdBuildId}"

  // add LABELs to dockerfiles
  def docker = new com.mirantis.mcp.Docker()
  docker.setDockerfileLabels("./calicoctl/Dockerfile.calicoctl",
                             ["docker.imgTag=${imgTag}",
                              "calico.buildImage=${buildImage}",
                              "calico.birdclUrl=${birdclUrl}"])

  docker.setDockerfileLabels("./calico_node/Dockerfile",
                             ["docker.imgTag=${imgTag}",
                              "calico.buildImage=${buildImage}",
                              "calico.felixImage=${felixImage}",
                              "calico.confdUrl=${confdUrl}",
                              "calico.birdUrl=${birdUrl}",
                              "calico.bird6Url=${bird6Url}",
                              "calico.birdclUrl=${birdclUrl}"])

  // Start build section
  stage ('Build calico/ctl image'){
    sh """
      make calico/ctl \
        CTL_CONTAINER_NAME=${ctlName} \
        PYTHON_BUILD_CONTAINER_NAME=${buildImage} \
        BIRDCL_URL=${birdclUrl}
    """
  }


  stage('Build calico/node'){
    sh """
      make calico/node \
        NODE_CONTAINER_NAME=${nodeName} \
        PYTHON_BUILD_CONTAINER_NAME=${buildImage} \
        FELIX_CONTAINER_NAME=${felixImage} \
        CONFD_URL=${confdUrl} \
        BIRD_URL=${birdUrl} \
        BIRD6_URL=${bird6Url} \
        BIRDCL_URL=${birdclUrl}
    """
  }


  return [
    CTL_CONTAINER_NAME:"${ctlName}",
    NODE_CONTAINER_NAME:"${nodeName}",
    CALICO_NODE_IMAGE_REPO:"${nodeImage}",
    CALICOCTL_IMAGE_REPO:"${ctlImage}",
    CALICO_VERSION: "${imgTag}"
  ]

}
