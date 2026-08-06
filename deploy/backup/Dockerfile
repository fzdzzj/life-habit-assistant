ARG BASE_IMAGE=mysql:8.0
FROM ${BASE_IMAGE}

RUN microdnf install -y cronie \
    && microdnf clean all

COPY backup.sh /usr/local/bin/backup.sh
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /usr/local/bin/backup.sh /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
