package com.myorg;

import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

import java.util.Arrays;

public class CursoAwsCdkApp {
    public static void main(final String[] args) {
        App app = new App();

        VpcStack vpcStack = new VpcStack(app, "Vpc");

        ClusterStack clusterStack = new ClusterStack(app, "Cluster", vpcStack.getVpc());
        clusterStack.addDependency(vpcStack);

        RdsStack rdsStack = new RdsStack(app, "rds", vpcStack.getVpc());
        rdsStack.addDependency(vpcStack);

        SnsStack snsStack = new SnsStack(app, "Sns");


        Service01Stack service01 = new Service01Stack(app, "Service01", clusterStack.getCluter(), snsStack.getProductsTopic());
        service01.addDependency(clusterStack);
        service01.addDependency(rdsStack);
        service01.addDependency(snsStack);


        DynamoDBStack dynamoDBStack = new DynamoDBStack(app, "DynamoDB");

        ProductSubscriberStack productSubscriber = new ProductSubscriberStack(app, "ProductSubscriber", clusterStack.getCluter(), snsStack.getProductsTopic(), dynamoDBStack.getProductEventsTable());
        productSubscriber.addDependency(snsStack);
        productSubscriber.addDependency(clusterStack);
        productSubscriber.addDependency(dynamoDBStack);

        app.synth();
    }
}

