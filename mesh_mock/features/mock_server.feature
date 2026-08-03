Feature: Multi-client network simulation and routing
  As a Wear OS developer
  I want to test simultaneous message traffic between watches
  So that the simulator correctly routes messages in parallel

  Scenario: Bidirectional parallel message exchange between two client nodes
    Given the Meshtastic mock server is running
    When Client A connects to the simulator
    And Client B connects to the simulator
    When Client A sends the message "Hello from Client A" to Client B's node
    Then Client B should receive the message "Hello from Client A" sent by Client A
    When Client B sends the message "Reply from Client B" to Client A's node
    Then Client A should receive the message "Reply from Client B" sent by Client B
