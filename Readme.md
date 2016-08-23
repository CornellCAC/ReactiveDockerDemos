# Running

You can run individual examples from [sbt](http://www.scala-sbt.org). Once you start sbt, you can compile all examples by running 'compile'. A particular example can be run by using `run-main`, e.g., ` run-main ScriptRunnerPar`.


# Notes

## Docker system configuraiton

### System config

Add the following to `docker.conf` (e.g., in Ubuntu it is `/etc/init/docker.conf`; depending on your OS version, you may be redirected to add the config to a different file, e.g., `/etc/default/docker`):
```
DOCKER_OPTS='-H tcp://0.0.0.0:4243 -H unix:///var/run/docker.sock'
```

This will bind Docker to port 4243, making the remote API accessible. After saving, restart docker. To test, try a docker command like `ps`:

```
docker -H tcp://0.0.0.0:4243 ps
```

### User config

Make sure user running code is in the docker group: 

```
sudo usermod -aG docker myusername
```