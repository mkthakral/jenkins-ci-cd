#!/bin/bash

# Set Required Paramerters
ARTIFACT_REPO_PATH=/home/jenkins_user/ARTIFACT_REPO
HYBRIS_INSTAL_DIR=/opt/hybris
WSDL_RESOURCE=/opt/hybris/hybris/bin/custom/gyservices/resources/wsdl/
WSDL_TARGET=/data/WSDL

# Check directory exists or not
if [ -d $ARTIFACT_REPO_PATH ]; then echo "Directory exists"; else mkdir -p $ARTIFACT_REPO_PATH; fi

# Unzip build artifact to common directory
unzip -o $ARTIFACT_REPO_PATH/artifact_*.zip -d $ARTIFACT_REPO_PATH/

# Unzip config artifact to common directory
unzip -o $ARTIFACT_REPO_PATH/config-*.zip -d $ARTIFACT_REPO_PATH/

# Unzip required config directory to hybris
HOST_IDENTIFIER=`hostname | awk -F "." '{ print $1 }' | awk -F "ec" '{ print $2 }'`
if [ $HOST_IDENTIFIER = 'dev1' ]
  then
    sudo rsync --delete -apvze $ARTIFACT_REPO_PATH/config-dev/ /opt/hybris/hybris/config
  else
    sudo rsync --delete -apvze $ARTIFACT_REPO_PATH/config-$HOST_IDENTIFIER/ /opt/hybris/hybris/config
fi

# Unzip and place Hybris Code to its location
sudo unzip -o $ARTIFACT_REPO_PATH/hybrisServer-Licence-*.zip -d $HYBRIS_INSTAL_DIR
sudo unzip -o $ARTIFACT_REPO_PATH/hybrisServer-AllExtensions-*.zip -d $HYBRIS_INSTAL_DIR
sudo unzip -o $ARTIFACT_REPO_PATH/hybrisServer-Platform-*.zip -d $HYBRIS_INSTAL_DIR

# copy wsdls to common directory Location 
if [ -d $WSDL_TARGET ]; then echo "Directory exists"; else mkdir -p $WSDL_TARGET; fi
sudo rsync --delete -apvze $WSDL_RESOURCE $WSDL_TARGET

#Set permission for Hybris
sudo chown -R hybris.hybris $HYBRIS_INSTAL_DIR

# Stop and Start Hybris
sudo service hybris stop
sudo service hybris start

# Check Hybris is Up or not
wget --no-check-certificate https://`hostname`:9002/admin >>/dev/null 2>>/dev/null
if [ $? -eq 0 ]; then echo "Hybris on `hostname` is Up"; else count=`expr $count + 1`; if [ $count -gt 4 ]; then echo Error :  Please check console on hybris node and logs for errors.; exit 99; fi; sleep 150; fi

# Clean the artifact directory
rm -rf $ARTIFACT_REPO_PATH/*