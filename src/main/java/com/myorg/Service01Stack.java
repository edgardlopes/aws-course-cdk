package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class Service01Stack extends Stack {
    public Service01Stack(final Construct scope, final String id, Cluster cluster, SnsTopic productEventsTopic) {
        this(scope, id, null, cluster,productEventsTopic);
    }

    public Service01Stack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic productEventsTopic) {
        super(scope, id, props);

        Map<String, String> env = new HashMap<>();
        env.put("SPRING_DATASOURCE_URL", "jdbc:mariadb://" + Fn.importValue("rds-endpoint") + ":3306/aws_project01?createDatabaseIfNotExist=true");
        env.put("SPRING_DATASOURCE_USERNAME", "admin");
        env.put("SPRING_DATASOURCE_PASSWORD", Fn.importValue("rds-password"));
        env.put("AWS_SNS_TOPIC_PRODUCTS_EVENT_ARN", productEventsTopic.getTopic().getTopicArn());

        ApplicationLoadBalancedFargateService alb = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB01")
                .serviceName("service01")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2) //numero de instancias
                .listenerPort(8080)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("aws_project01")
                                .image(ContainerImage.fromRegistry("edgardlopes/aws_project01:1.0.8"))
                                .containerPort(8080)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder
                                                .create(this, "Service01LogGroup")
                                                .logGroupName("Service01")
                                                .removalPolicy(RemovalPolicy.DESTROY).build())
                                        .streamPrefix("Service01")
                                        .build()))
                                .environment(env)
                                .build()
                )
                .publicLoadBalancer(true)
                .build();

        alb.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                        .path("/actuator/health")
                        .port("8080")
                        .healthyHttpCodes("200")
                .build());


        ScalableTaskCount scalableTaskCount = alb.getService().autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(2) // comeca em duas instancias
                .maxCapacity(4) // escala ate 4 instancias
                .build());

        scalableTaskCount.scaleOnCpuUtilization("Service01AutoScaling", CpuUtilizationScalingProps.builder()
                        .targetUtilizationPercent(50) // limiar de escala em % de cpu
                        .scaleInCooldown(Duration.seconds(60)) //escala se ficar 60 segundos acima do limite
                        .scaleOutCooldown(Duration.seconds(60))
                .build());

        productEventsTopic.getTopic().grantPublish(alb.getTaskDefinition().getTaskRole());


    }
}
