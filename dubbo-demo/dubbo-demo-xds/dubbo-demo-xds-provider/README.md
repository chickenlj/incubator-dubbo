## Deploy to Kubernetes
Run the following command to deploy application to kubernetes.

```shell
kubectl apply -f
```

## Build your own image

```shell
docker build -f ./Dockerfile --build-arg APP_FILE=dubbo-demo-xds-provider-3.3.0-beta.5-SNAPSHOT.jar -t xds-provider:1.0 .
```

```shell
docker tag xds-provider:1.0 {your-image-repo}:{version}
```

```shell
docker push {your-image-repo}:{version}
```


