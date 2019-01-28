# Extend vert.x image
FROM vertx/vertx3
MAINTAINER leon_xi@163.com

#                                                       (1)
ENV VERTICLE_NAME com.xiaoji.duan.auo.MainVerticle
ENV VERTICLE_FILE auo-1.0.0-SNAPSHOT-fat.jar

# Set the location of the verticles
ENV VERTICLE_HOME /usr/verticles

EXPOSE 8080

ADD target/$VERTICLE_FILE $VERTICLE_HOME/
ADD auo.json $VERTICLE_HOME/

# Launch the verticle
WORKDIR $VERTICLE_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["java -cp $VERTICLE_HOME/$VERTICLE_FILE io.vertx.core.Launcher run $VERTICLE_NAME -conf auo.json"]
