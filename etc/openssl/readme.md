# Generate certificates
```
./certs.sh
```

## Misc
Some code snippets...

```
CA_PASS=changeit
CA_SUBJ="/C=DE/ST=SL/L=Germany/O=tdlabs/CN=ca"

SVC_PASS=changeit
SVC_SUBJ="/C=DE/ST=SL/L=Germany/O=tdlabs/CN=service"
```

### Generate root ca

```
openssl req \
  -newkey rsa:4096 \
  -x509 \
  -keyout root-ca.key \
  -out root-ca.crt \
  -days 3650 \
  -passout "pass:$CA_PASS" \
  -subj "$CA_SUBJ"
```

### Generate service key and csr
```
openssl req \
  -new \
  -newkey rsa:2048 \
  -keyout service.key \
  -out service.csr \
  -passout "pass:$SVC_PASS" \
  -subj "$SVC_SUBJ"
```

### Generate service cert
```
openssl x509 \
  -req \
  -in service.csr \
  -CA root-ca.crt \
  -CAkey root-ca.key \
  -passin "pass:$CA_PASS" \
  -CAcreateserial \
  -out service.crt \
  -days 3650 \
  -sha256
```

### Use client certificate to access service
```
curl --cert certs/client1.crt:changeit --key certs/client1.key --cacert certs/root-ca.crt -v https://apps.tdlabs.local:8443/
curl --cert certs/client1.crt --key certs/client1.key-nopwd --cacert certs/root-ca.crt -v https://apps.tdlabs.local:8443/
```

### Create Keystore with all client certificates
```
DST_KS_NAME=certs/clients-combined.p12
DST_KS_STOREPASS=changeit

keytool  \
  -genkey  \
  -alias dummy  \
  -keyalg RSA  \
  -keysize 4096  \
  -dname "CN=dummy"  \
  -keypass $DST_KS_STOREPASS  \
  -keystore $DST_KS_NAME  \
  -storepass $DST_KS_STOREPASS  \
  -deststoretype pkcs12

keytool -delete \
 -alias dummy \
 -keystore $DST_KS_NAME  \
 -storepass $DST_KS_STOREPASS  \
 -storetype pkcs12

SRC_KS_NAME=certs/client1.p12
SRC_KS_STOREPASS="changeit"
ALIAS=client1

keytool -importkeystore \
  -srckeystore $SRC_KS_NAME \
  -destkeystore $DST_KS_NAME \
  -srcstoretype PKCS12 \
  -srcstorepass "$SRC_KS_STOREPASS" \
  -srcalias $ALIAS \
  -destalias $ALIAS \
  -deststoretype PKCS12 \
  -deststorepass $DST_KS_STOREPASS \
  -destkeypass $DST_KS_STOREPASS

SRC_KS_NAME=certs/client2.p12
SRC_KS_STOREPASS="changeit"
ALIAS=client2

keytool -importkeystore \
  -srckeystore $SRC_KS_NAME \
  -destkeystore $DST_KS_NAME \
  -srcstoretype PKCS12 \
  -srcstorepass "$SRC_KS_STOREPASS" \
  -srcalias $ALIAS \
  -destalias $ALIAS \
  -deststoretype PKCS12 \
  -deststorepass $DST_KS_STOREPASS \
  -destkeypass $DST_KS_STOREPASS
```

### Generate truststore
```
trustStore=certs/clients-truststore.p12
trustStoreFormat=pkcs12
trustStorePassword=changeit
alias=ca
cert=certs/root-ca.crt

 keytool -import \
    -noprompt \
    -alias "$alias" \
    -file "$cert" \
    -keystore "$trustStore" \
    -storepass "$trustStorePassword" \
    -storetype "$trustStoreFormat"
```