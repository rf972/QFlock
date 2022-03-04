#!/bin/bash
source docker/spark_version
source docker/setup.sh
echo $SPARK_VERSION
tar -xzf docker/${SPARK_PACKAGE} -C build
mv build/${SPARK_PACKAGE_FOLDER} build/spark-${SPARK_VERSION}
