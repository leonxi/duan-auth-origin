# Extend vert.x image
FROM vertx/vertx3
MAINTAINER leon_xi@163.com

#                                                       (1)
ENV VERTICLE_NAME com.xiaoji.duan.auo.MainVerticle
ENV VERTICLE_FILE auo-1.0.0-SNAPSHOT-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

# Install Maven
ARG MAVEN_VERSION=3.6.0
ARG USER_HOME_DIR="/root"
ARG SHA=fae9c12b570c3ba18116a4e26ea524b29f7279c17cbaadc3326ca72927368924d9131d11b9e851b8dc9162228b6fdea955446be41207a5cfc61283dd8a561d2f
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"

COPY settings-docker.xml /usr/share/maven/ref/

# Install DUAN-Auth-Origin
ARG DUAN_SOURCE_PATH="/opt/xiaoji/duan/duan-auth-origin"

RUN mkdir -p $DUAN_SOURCE_PATH

WORKDIR $DUAN_SOURCE_PATH

COPY * ./
COPY src ./src

RUN mvn clean package

RUN mkdir -p $VERTICLE_HOME \
  && cp $DUAN_SOURCE_PATH/target/$VERTICLE_FILE $VERTICLE_HOME/$VERTICLE_FILE
  
RUN rm -Rf $DUAN_SOURCE_PATH \
  && rm -Rf /tmp \
  && rm -Rf $USER_HOME_DIR/.m2

EXPOSE 8080

ADD auo.json $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -cp $VERTICLE_HOME/$VERTICLE_FILE io.vertx.core.Launcher run $VERTICLE_NAME -conf auo.json"]
