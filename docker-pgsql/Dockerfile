FROM postgres:14

LABEL maintainer="gishu@hotmail.com"

ADD ./init-db.sh /docker-entrypoint-initdb.d/
ADD ./kaching.dmp /docker-entrypoint-initdb.d/
RUN chown $POSTGRES_USER:postgres /docker-entrypoint-initdb.d/*

ENTRYPOINT ["docker-entrypoint.sh"]
EXPOSE 5432
CMD ["postgres"]
