# keycloak-username-and-uidnumber-mapper
A mapper that will generate usernames and uidNumbers for users, to be sent back to LDAP

It should be configured as follows:
-----------------------------------

The Sync Mode Override MUST be set to import - this means the mapper will only run once, on the first import.

The uidNumber file path should be a file on the system Keycloak is running on. It should be in the following format:

```
nextId=10000001
```

The Username Prefix can be any string of your choice. It will be prepended to the incrementing uidNumber.
e.g. if my prefix is user the users will be added as follows:
user0001, user0002, user0003 etc.
The last four digits of the nextId number are taken for this.

gidNumber should just be the gidNumber for any users it creates.

SLURM account should be the SLURM account the user will be attached to.

Login Shell can be any shell that's installed on the systems, all users added this way will be given this.

![image](https://user-images.githubusercontent.com/3112077/217306087-689a77d7-2483-45e1-b583-a76ec6ff0f85.png)

How to deploy:
--------------

Run `mvn package` to build a .jar file.

Assuming you have installed Keycloak (extracted the Keycloak files) to `/opt/keycloak/`, copy the .jar to `/opt/keycloak/providers/`.

Start Keycloak - for example, using this SystemD unit:

```
[Install]
WantedBy=multi-user.target

[Service]
ExecStart=/opt/keycloak/bin/kc.sh start --spi-theme-static-max-age=-1 --spi-theme-cache-themes=false --spi-theme-cache-templates=false
Restart=no
SuccessExitStatus=0 143
User=root

[Unit]
After=syslog.target
After=network.target
Description=Keycloak
```