package com.myorg;

import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.constructs.Construct;
// import software.amazon.awscdk.Duration;
// import software.amazon.awscdk.services.sqs.Queue;

public class DynamoDBStack extends Stack {

    private final Table productEventsTable;
    public DynamoDBStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public DynamoDBStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        productEventsTable = Table.Builder.create(this, "ProductEventsDB")
                .tableName("product-events")
                .billingMode(BillingMode.PROVISIONED)
                .readCapacity(1)
                .writeCapacity(1)
                .partitionKey(Attribute.builder().name("pk").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("sk").type(AttributeType.STRING).build())
                .timeToLiveAttribute("ttl")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        // The code that defines your stack goes here

        // example resource
        // final Queue queue = Queue.Builder.create(this, "CursoAwsCdkQueue")
        //         .visibilityTimeout(Duration.seconds(300))
        //         .build();
    }

    public Table getProductEventsTable() {
        return productEventsTable;
    }
}
