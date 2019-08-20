#!/bin/bash

function create_root_ca() {

  local caName=$1
  local caPassword=$2
  local caCN=$3
  local configFileTemplate=$4
  local configFile="certs/$caCN.txt"

  echo "Generating Certificate for CA with alias $certAlias with template $configFileTemplate"

   sed  "s/\$certCN/$caCN/g;" "$configFileTemplate" > $configFile

  openssl req \
    -newkey rsa:4096 \
    -x509 \
    -keyout "$caName".key \
    -out "$caName".crt \
    -days 3650 \
    -passout "pass:$caPassword" \
    -config <( cat "$configFile" )

  echo "Generated Certificate for CA with alias $caCN with template $configFileTemplate"
  echo
}

function create_client_cert(){

  local certName=$1
  local certPass=$2
  local certCN=$3
  local certAlias=$4
  local configFileTemplate=$5
  local configFile="certs/$certAlias.txt"

  echo "Generating Certificate for client with alias $certAlias with template $configFileTemplate"

  sed  "s/\$certCN/$certCN/g;" "$configFileTemplate" > $configFile

  # Generate key and csr
  openssl req \
  -new \
  -newkey rsa:2048 \
  -keyout "$certName.key" \
  -out "$certName.csr" \
  -passout "pass:$certPass" \
  -config <( cat "$configFile" )

  # Export key without password
  openssl rsa \
    -in "$certName.key" \
    -passin "pass:$certPass" \
    -out "$certName.key-nopwd"

# Generate cert
  openssl x509 \
  -req \
  -in "$certName.csr" \
  -CA certs/root-ca.crt \
  -CAkey certs/root-ca.key \
  -passin "pass:$CA_PASS" \
  -CAcreateserial \
  -extensions req_ext \
  -extfile "$configFile" \
  -out "$certName.crt" \
  -days 3650 \
  -sha256

# Generate pkcs12 file
  openssl pkcs12 \
  -export \
  -name "$certAlias" \
  -inkey "$certName.key" \
  -passin "pass:$certPass" \
  -passout "pass:$certPass" \
  -in "$certName.crt" \
  -certfile certs/root-ca.crt \
  -out "$certName.p12"

  echo "Generated Certificate for client with alias $certAlias with template $configFileTemplate"
  echo
}

function create_service_cert(){

  local certName=$1
  local certPass=$2
  local certCN=$3
  local certAlias=$4
  local configFileTemplate=$5

  echo "Generating Certificate for service with alias $certAlias with template $configFileTemplate"

  local configFile="certs/$certAlias.txt"

  sed  "s/\$certCN/$certCN/g;" "$configFileTemplate" > $configFile

  # Generate key and csr
  openssl req \
  -new \
  -newkey rsa:2048 \
  -keyout "$certName.key" \
  -out "$certName.csr" \
  -passout "pass:$certPass" \
  -config <( cat $configFile )

  # Export key without password
  openssl rsa \
    -in "$certName.key" \
    -passin "pass:$certPass" \
    -out "$certName.key-nopwd"

# Generate cert
  openssl x509 \
  -req \
  -in "$certName.csr" \
  -CA certs/root-ca.crt \
  -CAkey certs/root-ca.key \
  -passin "pass:$CA_PASS" \
  -CAcreateserial \
  -extensions req_ext \
  -extfile $configFile \
  -out "$certName.crt" \
  -days 3650 \
  -sha256

# Generate pkcs12 file
  openssl pkcs12 \
  -export \
  -name "$certAlias" \
  -inkey "$certName.key" \
  -passin "pass:$certPass" \
  -passout "pass:$certPass" \
  -in "$certName.crt" \
  -certfile certs/root-ca.crt \
  -out "$certName.p12"

  echo "Generated Certificate for service with alias $certAlias with template $configFileTemplate"
  echo
}

function make_certs() {

    local caPassword="$CA_PASS"

    create_root_ca "certs/root-ca" "$caPassword" "root-ca"  templates/ca_details_template.txt

    local certPassword="$SERVICE_PASS"

    create_service_cert "certs/service1" "$certPassword" "service1" "service1" templates/service_details_template.txt

    certPassword="$CLIENT_PASS"
    create_client_cert "certs/client1" "$certPassword" "client1" "client1" templates/client_details_template.txt
    create_client_cert "certs/client2" "$certPassword" "client2" "client2" templates/client_details_template.txt
    create_client_cert "certs/client3" "$certPassword" "client3" "client3" templates/client_details_template.txt
    create_client_cert "certs/client4" "$certPassword" "client4" "client4" templates/client_details_template.txt

    local trustStorePassword="$TRUST_STORE_PASS"
    local trustStore="certs/service1-truststore.pkcs12"
    local trustStoreFormat="pkcs12"
    add_cert_to_truststore "$trustStore" "$trustStoreFormat" "$trustStorePassword" "client1" "certs/client1.crt"
    add_cert_to_truststore "$trustStore" "$trustStoreFormat" "$trustStorePassword" "client2" "certs/client2.crt"
    # add_cert_to_truststore "$trustStore" "$trustStoreFormat" "$trustStorePassword" "client3" "certs/client3.crt"
    add_cert_to_truststore "$trustStore" "$trustStoreFormat" "$trustStorePassword" "client4" "certs/client4.crt"

    local keystoreClientsCombined="certs/clients-combined-keystore.pkcs12"
    local keystoreClientsFormat="pkcs12"
    local keystoreClientsCombinedPassword="changeit"

    add_cert_to_keystore "$keystoreClientsCombined" "$keystoreClientsFormat" "$keystoreClientsCombinedPassword" "client1" certs/client1.p12 "changeit"
    add_cert_to_keystore "$keystoreClientsCombined" "$keystoreClientsFormat" "$keystoreClientsCombinedPassword" "client2" certs/client2.p12 "changeit"
    add_cert_to_keystore "$keystoreClientsCombined" "$keystoreClientsFormat" "$keystoreClientsCombinedPassword" "client3" certs/client3.p12 "changeit"
    add_cert_to_keystore "$keystoreClientsCombined" "$keystoreClientsFormat" "$keystoreClientsCombinedPassword" "client4" certs/client4.p12 "changeit"

    local trustStoreClientsCombined="certs/clients-combined-truststore.pkcs12"
    local trustStoreClientsFormat="pkcs12"
    local trustStoreClientsCombinedPassword="changeit"

    add_cert_to_truststore "$trustStoreClientsCombined" "$trustStoreClientsFormat" "$trustStoreClientsCombinedPassword" "root-ca" certs/root-ca.crt
}

function add_cert_to_truststore() {

  local trustStore=$1
  local trustStoreFormat=$2
  local trustStorePassword=$3
  local alias=$4
  local cert=$5

  echo "Adding $cert to truststore $trustStore..."

# Don't include CA as we want to explicitly list allowed client certificates!
# -trustcacerts \

  keytool -import \
    -noprompt \
    -alias "$alias" \
    -file "$cert" \
    -keystore "$trustStore" \
    -storepass "$trustStorePassword" \
    -storetype "$trustStoreFormat"
}

function add_cert_to_keystore() {

  local keyStore=$1
  local keyStoreFormat=$2
  local keyStorePassword=$3
  local alias=$4
  local srcKeystore=$5
  local srcKeystorePassword=$6

  echo "Importing keystore $srcKeystore alias $alias into keystore $keyStore..."

  keytool -importkeystore \
      -srckeystore "$srcKeystore" \
      -destkeystore "$keyStore" \
      -srcstoretype "$keyStoreFormat" \
      -srcstorepass "$srcKeystorePassword" \
      -srcalias "$alias" \
      -destalias "$alias" \
      -deststoretype "$keyStoreFormat" \
      -deststorepass "$keyStorePassword" \
      -destkeypass "$keyStorePassword"

}
function clear_certs() {

    rm -rf certs/*
}

. certs.env
clear_certs
make_certs