package com.myorg;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.applicationautoscaling.EnableScalingProps;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedTaskImageOptions;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.services.events.targets.SnsTopic;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.sns.subscriptions.SqsSubscription;
import software.amazon.awscdk.services.sqs.DeadLetterQueue;
import software.amazon.awscdk.services.sqs.Queue;
import software.amazon.awscdk.services.sqs.QueueEncryption;
import software.constructs.Construct;

import java.util.HashMap;
import java.util.Map;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class ProductSubscriberStack extends Stack {
    public ProductSubscriberStack(final Construct scope, final String id, Cluster cluster, SnsTopic snsTopic, Table table) {
        this(scope, id, null, cluster, snsTopic, table);
    }

    public ProductSubscriberStack(final Construct scope, final String id, final StackProps props, Cluster cluster, SnsTopic snsTopic, Table table) {
        super(scope, id, props);

        Queue productEventsDlq = Queue.Builder.create(this, "ProductEventsDql")
                .queueName("product-events-dlq")
                .enforceSsl(false)
                .encryption(QueueEncryption.UNENCRYPTED)
                .build();

        DeadLetterQueue deadLetterQueue = DeadLetterQueue.builder()
                .queue(productEventsDlq)
                .maxReceiveCount(3)
                .build();

        Queue productEventsQueue = Queue.Builder.create(this, "ProductEvents")
                .queueName("product-events")
                .enforceSsl(false)
                .encryption(QueueEncryption.UNENCRYPTED)
                .deadLetterQueue(deadLetterQueue)
                .build();

        SqsSubscription sqsSubscription = SqsSubscription.Builder.create(productEventsQueue).build();
        snsTopic.getTopic().addSubscription(sqsSubscription);

        Map<String, String> env = new HashMap<>();
        env.put("AWS_REGION", "us-east-1");
        env.put("AWS_SQS_QUEUE_PRODUCT_EVENTS_NAME", productEventsQueue.getQueueName());

        ApplicationLoadBalancedFargateService alb = ApplicationLoadBalancedFargateService.Builder.create(this, "ALB02")
                .serviceName("productsubscriber")
                .cluster(cluster)
                .cpu(512)
                .memoryLimitMiB(1024)
                .desiredCount(2) //numero de instancias
                .listenerPort(9090)
                .taskImageOptions(
                        ApplicationLoadBalancedTaskImageOptions.builder()
                                .containerName("productsubscriber")
                                .image(ContainerImage.fromRegistry("edgardlopes/productsubscriber:1.0.5"))
                                .containerPort(9090)
                                .logDriver(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                        .logGroup(LogGroup.Builder
                                                .create(this, "productsubscriberLogGroup")
                                                .logGroupName("productsubscriber")
                                                .removalPolicy(RemovalPolicy.DESTROY).build())
                                        .streamPrefix("productsubscriber")  
                                        .build()))
                                .environment(env)
                                .build()
                )
                .publicLoadBalancer(true)
                .build();


        alb.getTargetGroup().configureHealthCheck(HealthCheck.builder()
                .path("/actuator/health")
                .port("9090")
                .healthyHttpCodes("200")
                .build());

        ScalableTaskCount scalableTaskCount = alb.getService().autoScaleTaskCount(EnableScalingProps.builder()
                .minCapacity(2) // comeca em duas instancias
                .maxCapacity(4) // escala ate 4 instancias
                .build());

        scalableTaskCount.scaleOnCpuUtilization("ProductSubscriberAutoScaling", CpuUtilizationScalingProps.builder()
                .targetUtilizationPercent(50) // limiar de escala em % de cpu
                .scaleInCooldown(Duration.seconds(60)) //escala se ficar 60 segundos acima do limite
                .scaleOutCooldown(Duration.seconds(60))
                .build());

//        productEventsTopic.getTopic().grantPublish(alb.getTaskDefinition().getTaskRole());
        productEventsQueue.grantConsumeMessages(alb.getTaskDefinition().getTaskRole());

        table.grantReadWriteData(alb.getTaskDefinition().getTaskRole());
    }
}
