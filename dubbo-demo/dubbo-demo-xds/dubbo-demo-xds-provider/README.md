## Deploy to Kubernetes
Run the following command to deploy application to kubernetes.

```shell
kubectl apply -f ./manifests/Deployment.yml
```

## Build your own image

Run `./mvnw clean package -DskipTests` locally first, then run the following command to build image:

```shell
docker build -f ./Dockerfile -t xds-provider:1.0 .
```

```shell
docker tag xds-provider:1.0 {your-image-repo}:{version}
```

```shell
docker push {your-image-repo}:{version}
```


