#!/bin/sh

#
# Copyright 2023 Accenture Global Solutions Limited
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Install executor software packages

apt-get update
apt-get upgrade -y
apt-get install -y openssh-server openssh-client sudo

# Set up ssh server
/etc/init.d/ssh start

# Set up ssh user
useradd -c ci_user -m -G sudo -p ${SSH_USER_PASSWORD} ${SSH_USER_NAME}
ssh-keygen -t rsa -b 2045 -C ci_user -f ssh_key
mkdir /home/${SSH_USER_NAME}/.ssh
cat ssh_key.pub >> /home/${SSH_USER_NAME}/.ssh/authorized_keys
chown -R ${SSH_USER_NAME}:${SSH_USER_NAME} /home/${SSH_USER_NAME}/.ssh
chmod 700 /home/${SSH_USER_NAME}/.ssh
chmod 600 /home/${SSH_USER_NAME}/.ssh/authorized_keys

# test connection
ssh -o StrictHostKeyChecking=accept-new -i ssh_key -p ${SSH_PORT} ${SSH_USER_NAME}@${SSH_HOST} echo connected



apt-get install -y python${PYTHON_VERSION} python${PYTHON_VERSION}-venv
mkdir /opt/trac
chown ${SSH_USER_NAME}:${SSH_USER_NAME} /opt/trac

ssh -i ssh_key -p ${SSH_PORT} ${SSH_USER_NAME}@${SSH_HOST} << ENDSSH

  mkdir /opt/trac/jobs

  python${PYTHON_VERSION} -m venv /opt/trac/venv
  . /opt/trac/venv/bin/activate

ENDSSH
