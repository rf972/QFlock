#!/bin/bash

# Include the setup for our cached local directories. (.m2, .ivy2, etc)
source docker/setup.sh
source docker/spark_version
mkdir -p "${ROOT_DIR}/volume/logs"
rm -f "${ROOT_DIR}/volume/logs/master*.log"

mkdir -p "${ROOT_DIR}/volume/status"
rm -f "${ROOT_DIR}/volume/status/MASTER*"

mkdir -p "${ROOT_DIR}/volume/metastore"
mkdir -p "${ROOT_DIR}/volume/user/hive"

CMD="sleep 365d"
RUNNING_MODE="daemon"
START_LOCAL="NO"
if [ ! -d spark.config ]; then
  START_LOCAL="YES"
else
  DOCKER_HOSTS="$(cat spark.config | grep DOCKER_HOSTS)"
  IFS='=' read -a IP_ARRAY <<< "$DOCKER_HOSTS"
  DOCKER_HOSTS=${IP_ARRAY[1]}
  HOSTS=""
  IFS=',' read -a IP_ARRAY <<< "$DOCKER_HOSTS"
  for i in "${IP_ARRAY[@]}"
  do
    HOSTS="$HOSTS --add-host=$i"
  done
  DOCKER_HOSTS=$HOSTS
  echo "Docker Hosts: $DOCKER_HOSTS"

  LAUNCHER_IP="$(cat spark.config | grep LAUNCHER_IP)"
  IFS='=' read -a IP_ARRAY <<< "$LAUNCHER_IP"
  LAUNCHER_IP=${IP_ARRAY[1]}
  echo "LAUNCHER_IP: $LAUNCHER_IP"
fi
DOCKER_ID=""
if [ $RUNNING_MODE = "interactive" ]; then
  DOCKER_IT="-i -t"
fi

if [ ${START_LOCAL} == "YES" ]; then
  DOCKER_RUN="docker run ${DOCKER_IT} --rm \
  -p 5006:5006 \
  --name sparklauncher-qflock --add-host=hdfs-server:172.18.0.3\
  --network qflock-net \
  -e MASTER=spark://sparkmaster:7077 \
  -e SPARK_CONF_DIR=/conf \
  -e SPARK_PUBLIC_DNS=localhost \
  --mount type=bind,source=$(pwd)/../,target=/qflock \
  -w /qflock/benchmark/src \
  --mount type=bind,source=$(pwd)/spark,target=/spark \
  --mount type=bind,source=$(pwd)/extensions/,target=/extensions \
  -v $(pwd)/conf/master:/opt/spark-$SPARK_VERSION/conf  \
  -v ${ROOT_DIR}/volume/metastore:/opt/volume/metastore \
  -v ${ROOT_DIR}/volume/user/hive:/user/hive \
  -v ${ROOT_DIR}/build/.m2:${DOCKER_HOME_DIR}/.m2 \
  -v ${ROOT_DIR}/build/.gnupg:${DOCKER_HOME_DIR}/.gnupg \
  -v ${ROOT_DIR}/build/.sbt:${DOCKER_HOME_DIR}/.sbt \
  -v ${ROOT_DIR}/build/.cache:${DOCKER_HOME_DIR}/.cache \
  -v ${ROOT_DIR}/build/.ivy2:${DOCKER_HOME_DIR}/.ivy2 \
  -v ${ROOT_DIR}/volume/status:/opt/volume/status \
  -v ${ROOT_DIR}/volume/logs:/opt/volume/logs \
  -v ${ROOT_DIR}/bin/:${DOCKER_HOME_DIR}/bin \
  -e RUNNING_MODE=${RUNNING_MODE} \
  -u ${USER_ID} \
  ${SPARK_DOCKER_NAME} ${CMD}"
else
  DOCKER_RUN="docker run ${DOCKER_IT} --rm \
  -p 5006:5006 \
  --name sparklauncher-qflock \
  --network qflock-net --ip ${LAUNCHER_IP} ${DOCKER_HOSTS} \
  -w /qflock/benchmark/src \
  -e MASTER=spark://sparkmaster:7077 \
  -e SPARK_CONF_DIR=/conf \
  -e SPARK_PUBLIC_DNS=localhost \
  -e SPARK_MASTER="spark://sparkmaster:7077" \
  -e SPARK_DRIVER_HOST=${LAUNCHER_IP} \
  --mount type=bind,source=$(pwd)/spark,target=/spark \
  --mount type=bind,source=$(pwd)/build,target=/build \
  -v $(pwd)/conf/master:/conf  \
  -v ${ROOT_DIR}/build/.m2:${DOCKER_HOME_DIR}/.m2 \
  -v ${ROOT_DIR}/build/.gnupg:${DOCKER_HOME_DIR}/.gnupg \
  -v ${ROOT_DIR}/build/.sbt:${DOCKER_HOME_DIR}/.sbt \
  -v ${ROOT_DIR}/build/.cache:${DOCKER_HOME_DIR}/.cache \
  -v ${ROOT_DIR}/build/.ivy2:${DOCKER_HOME_DIR}/.ivy2 \
  -v ${ROOT_DIR}/volume/status:/opt/volume/status \
  -v ${ROOT_DIR}/volume/logs:/opt/volume/logs \
  -v ${ROOT_DIR}/bin/:${DOCKER_HOME_DIR}/bin \
  -e RUNNING_MODE=${RUNNING_MODE} \
  -u ${USER_ID} \
  ${SPARK_DOCKER_NAME} ${CMD}"
fi
echo $DOCKER_RUN
echo "mode: $RUNNING_MODE"
if [ $RUNNING_MODE = "interactive" ]; then
  eval "${DOCKER_RUN}"
else
  eval "${DOCKER_RUN}" &
fi
