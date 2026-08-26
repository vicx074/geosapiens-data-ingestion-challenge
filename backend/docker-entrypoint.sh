#!/bin/sh
set -eu

# O volume nomeado é montado depois da criação da imagem e pode chegar pertencendo ao root.
# Ajustamos somente o diretório compartilhado e removemos privilégios antes de iniciar a JVM.
mkdir -p /data/uploads
chown geosapiens:geosapiens /data/uploads

exec su-exec geosapiens "$@"
