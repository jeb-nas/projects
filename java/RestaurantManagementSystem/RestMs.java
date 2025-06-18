package application;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class RestMs extends Application {
    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:XE"; // Example for XE
    private static final String DB_USER = "system";
    private static final String DB_PASSWORD = "oracle";

    static Map<String, String> menu = new HashMap<>();
    static Map<String, ArrayList<String>> cus_list = new HashMap<>();
    static Map<String, String> cu_tab = new HashMap<>();
    static Map<Integer, Boolean> tableAvailability = new HashMap<>();
    static Map<String, Integer> cu_bill = new HashMap<>();
    static Set<String> paidCustomers = new HashSet<>();

    // New field to store the current customer's name
    private String currentCustomerName = null;

    @Override
    public void start(Stage primaryStage) {
        showmenu();
        initializeTables(5);

        Label title = new Label("Mini Restaurant");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: darkblue;");
        Label label = new Label("Welcome to Mini Restaurant");
       
        Button customerBtn = new Button("Customer");
        Button adminBtn = new Button("Admin");
        Button exitBtn = new Button("Exit");

        VBox layout = new VBox(15, title, label, customerBtn, adminBtn, exitBtn);
        layout.setStyle("-fx-padding: 20; -fx-alignment: center;");

        Scene scene = new Scene(layout, 500, 500);

        customerBtn.setOnAction(e -> openCustomerNameInput()); // Call new method
        adminBtn.setOnAction(e -> openAdminMenu());
        exitBtn.setOnAction(e -> {
            if (!cu_bill.isEmpty() && !paidCustomers.containsAll(cu_bill.keySet())) {
                new Alert(Alert.AlertType.WARNING, "A customer hasn't paid yet. Please pay the bill before exiting.").show();
            } else {
                primaryStage.close();
            }
        });

        primaryStage.setTitle("Restaurant System");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void showmenu() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement("SELECT item_name, price FROM menu_items");
             ResultSet rs = pstmt.executeQuery()) {

            menu.clear();
            while (rs.next()) {
                String itemName = rs.getString("item_name");
                String price = rs.getString("price");
                menu.put(itemName, price);
            }
            System.out.println("Menu loaded from database.");
        } catch (SQLException e) {
            System.err.println("Error loading menu from database: " + e.getMessage());
            menu.put("rice", "30");
            menu.put("roti", "20");
            menu.put("chicken", "100");
            menu.put("biryani", "99");
            menu.put("desserts", "60");
        }
    }

    public static void initializeTables(int count) {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement("SELECT table_number, is_available FROM table_status ORDER BY table_number")) {

            ResultSet rs = pstmt.executeQuery();
            tableAvailability.clear();

            while (rs.next()) {
                int tableNumber = rs.getInt("table_number");
                boolean isAvailable = rs.getInt("is_available") == 1;
                tableAvailability.put(tableNumber, isAvailable);
            }

            if (tableAvailability.isEmpty() || tableAvailability.size() < count) {
                System.out.println("Initializing tables for the first time or extending.");
                for (int i = 1; i <= count; i++) {
                    if (!tableAvailability.containsKey(i)) {
                        tableAvailability.put(i, true);
                        try (PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO table_status (table_number, is_available) VALUES (?, ?)")) {
                            insertStmt.setInt(1, i);
                            insertStmt.setInt(2, 1);
                            insertStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error initializing tables from database: " + e.getMessage());
            for (int i = 1; i <= count; i++) {
                tableAvailability.put(i, true);
            }
        }
    }

    // New method to get customer name first
    private void openCustomerNameInput() {
        Stage stage = new Stage();
        VBox box = new VBox(10);
        box.setStyle("-fx-padding: 10; -fx-alignment: center;");

        Label prompt = new Label("Enter Customer Name:");
        TextField nameField = new TextField();
        nameField.setPromptText("Your name");
        nameField.setMaxWidth(200);

        Button enterBtn = new Button("Enter");
        Button cancelBtn = new Button("Cancel");

        HBox buttonBox = new HBox(10, enterBtn, cancelBtn);
        buttonBox.setStyle("-fx-alignment: center;");

        enterBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Customer name cannot be empty.").show();
            } else {
                currentCustomerName = name; // Set the current customer name
                stage.close();
                openCustomerMenu(); // Open the main customer menu after name is entered
            }
        });

        cancelBtn.setOnAction(e -> stage.close());

        box.getChildren().addAll(prompt, nameField, buttonBox);
        stage.setScene(new Scene(box, 300, 150));
        stage.setTitle("Customer Entry");
        stage.show();
    }

    // Original openCustomerMenu, now called after name input
    private void openCustomerMenu() {
        // If currentCustomerName is null, it means this was called directly without name input
        // This should ideally not happen with the new flow, but good for defensive programming.
        if (currentCustomerName == null || currentCustomerName.isEmpty()) {
            new Alert(Alert.AlertType.ERROR, "Customer name not set. Please restart customer session.").show();
            return;
        }

        Stage stage = new Stage();
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 10;");

        Label welcomeLabel = new Label("Welcome, " + currentCustomerName + "!");
        welcomeLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");

        Button showMenuBtn = new Button("Show Menu");
        Button orderBtn = new Button("Place Order");
        Button reserveBtn = new Button("Reserve Table");
        Button billBtn = new Button("Generate Bill");
        Button leaveBtn = new Button("Leave");
        Button backBtn = new Button("Back");

        layout.getChildren().addAll(welcomeLabel, showMenuBtn, orderBtn, reserveBtn, billBtn, leaveBtn, backBtn);

        showMenuBtn.setOnAction(e -> showMenuDialog());
        orderBtn.setOnAction(e -> placeOrder(currentCustomerName)); // Pass the current customer name
        reserveBtn.setOnAction(e -> reserveTable(currentCustomerName)); // Pass the current customer name
        billBtn.setOnAction(e -> generateBill(currentCustomerName)); // Pass the current customer name

        leaveBtn.setOnAction(e -> {
            // Check only for the current customer's payment status
            if (cu_bill.containsKey(currentCustomerName) && !paidCustomers.contains(currentCustomerName)) {
                new Alert(Alert.AlertType.WARNING, "Please pay your bill before leaving.").show();
            } else {
                // Remove specific customer data
                cus_list.remove(currentCustomerName);
                paidCustomers.remove(currentCustomerName);
                cu_tab.remove(currentCustomerName);
                cu_bill.remove(currentCustomerName);
                currentCustomerName = null; // Clear the current customer
                stage.close();
            }
        });

        backBtn.setOnAction(e -> {
            // Check only for the current customer's payment status
            if (cu_bill.containsKey(currentCustomerName) && !paidCustomers.contains(currentCustomerName)) {
                new Alert(Alert.AlertType.WARNING, "Please pay your bill before going back.").show();
            } else {
                currentCustomerName = null; // Clear the current customer
                stage.close();
            }
        });

        stage.setTitle("Customer Panel - " + currentCustomerName);
        stage.setScene(new Scene(layout, 350, 300));
        stage.show();
    }

    private void showMenuDialog() {
        StringBuilder sb = new StringBuilder("Menu:\n");
        menu.forEach((item, price) -> sb.append(item).append(": Rs.").append(price).append("\n"));
        new Alert(Alert.AlertType.INFORMATION, sb.toString()).show();
    }

    private void openAdminMenu() {
        Stage stage = new Stage();
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 10;");

        Button viewCustomers = new Button("Number of Customers");
        Button updatePrice = new Button("Update Item Price");
        Button addItem = new Button("Add New Item");
        Button showMenu = new Button("Show Menu");
        Button tableStatus = new Button("Table Status");
        Button backBtn = new Button("Back");

        viewCustomers.setOnAction(e -> {
            int count = cus_list.size();
            new Alert(Alert.AlertType.INFORMATION, "Total Customers Served: " + count).show();
        });

        updatePrice.setOnAction(e -> updateMenuPrice());
        addItem.setOnAction(e -> addNewMenuItem());
        showMenu.setOnAction(e -> showMenuDialog());

        tableStatus.setOnAction(e -> {
            StringBuilder status = new StringBuilder("Table Status:\n");
            for (int table = 1; table <= tableAvailability.size(); table++) {
                boolean available = tableAvailability.getOrDefault(table, true);
                status.append("Table ").append(table).append(": ").append(available ? "Available" : "Reserved").append("\n");
            }
            new Alert(Alert.AlertType.INFORMATION, status.toString()).show();
        });

        backBtn.setOnAction(e -> stage.close());

        layout.getChildren().addAll(viewCustomers, updatePrice, addItem, showMenu, tableStatus, backBtn);
        stage.setScene(new Scene(layout, 300, 250));
        stage.setTitle("Admin Panel");
        stage.show();
    }

    // Modified placeOrder to accept customer name
    private void placeOrder(String customerName) {
        Stage stage = new Stage();
        VBox box = new VBox(10);
        box.setStyle("-fx-padding: 10;");

        Label customerLabel = new Label("Placing order for: " + customerName);
        customerLabel.setStyle("-fx-font-weight: bold;");

        TextArea orderField = new TextArea();
        orderField.setPromptText("Enter food items (comma separated, e.g., rice,chicken)");

        Button submit = new Button("Submit Order");

        submit.setOnAction(e -> {
            String[] items = orderField.getText().split(",");
            ArrayList<String> orderList = new ArrayList<>();
            for (String item : items) {
                String trimmedItem = item.trim().toLowerCase();
                if (!trimmedItem.isEmpty()) {
                    orderList.add(trimmedItem);
                }
            }

            if (orderList.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Please enter at least one item.").show();
                return;
            }

            // Store in-memory
            cus_list.put(customerName, orderList);
            paidCustomers.remove(customerName);
            cu_bill.put(customerName, 0); // Initialize bill

            // Store in database (upsert: insert if not exists, update if exists)
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Check if customer already exists in customer_orders
                PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM customer_orders WHERE customer_name = ?");
                checkStmt.setString(1, customerName);
                ResultSet rs = checkStmt.executeQuery();
                rs.next();
                int count = rs.getInt(1);

                if (count > 0) {
                    // Update existing order
                    PreparedStatement updateStmt = conn.prepareStatement("UPDATE customer_orders SET order_details = ?, is_paid = ?, bill_amount = ? WHERE customer_name = ?");
                    updateStmt.setString(1, String.join(",", orderList));
                    updateStmt.setInt(2, 0); // Not paid yet for new order
                    updateStmt.setInt(3, 0); // Reset bill amount
                    updateStmt.setString(4, customerName);
                    updateStmt.executeUpdate();
                    new Alert(Alert.AlertType.INFORMATION, "Order updated for " + customerName + " and saved to database.").show();
                } else {
                    // Insert new order
                    PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO customer_orders (customer_name, order_details, is_paid, bill_amount) VALUES (?, ?, ?, ?)");
                    insertStmt.setString(1, customerName);
                    insertStmt.setString(2, String.join(",", orderList));
                    insertStmt.setInt(3, 0);
                    insertStmt.setInt(4, 0);
                    insertStmt.executeUpdate();
                    new Alert(Alert.AlertType.INFORMATION, "Order placed for " + customerName + " and saved to database.").show();
                }
                stage.close();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Error placing/updating order: " + ex.getMessage()).show();
            }
        });

        box.getChildren().addAll(customerLabel, orderField, submit);
        stage.setScene(new Scene(box, 350, 250));
        stage.setTitle("Place Order");
        stage.show();
    }

    // Modified reserveTable to accept customer name
    private void reserveTable(String customerName) {
        Stage stage = new Stage();
        VBox box = new VBox(10);
        box.setStyle("-fx-padding: 10;");

        Label customerLabel = new Label("Reserving table for: " + customerName);
        customerLabel.setStyle("-fx-font-weight: bold;");

        TextField tableField = new TextField();
        tableField.setPromptText("Enter table number (1-5)");

        Button reserveBtn = new Button("Reserve");

        reserveBtn.setOnAction(e -> {
            int table;
            try {
                table = Integer.parseInt(tableField.getText());
            } catch (NumberFormatException ex) {
                new Alert(Alert.AlertType.ERROR, "Please enter a valid table number").show();
                return;
            }
            if (table < 1 || table > 5) {
                new Alert(Alert.AlertType.ERROR, "Table number must be between 1 and 5").show();
                return;
            }

            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                PreparedStatement checkStmt = conn.prepareStatement("SELECT is_available FROM table_status WHERE table_number = ?");
                checkStmt.setInt(1, table);
                ResultSet rs = checkStmt.executeQuery();
                boolean isAvailableInDb = true;
                if (rs.next()) {
                    isAvailableInDb = rs.getInt("is_available") == 1;
                } else {
                    new Alert(Alert.AlertType.ERROR, "Table " + table + " not found in database.").show();
                    return;
                }

                if (isAvailableInDb) {
                    // Update in-memory
                    tableAvailability.put(table, false);
                    cu_tab.put(customerName, String.valueOf(table));

                    // Update in database
                    PreparedStatement updateTableStmt = conn.prepareStatement("UPDATE table_status SET is_available = 0 WHERE table_number = ?");
                    updateTableStmt.setInt(1, table);
                    updateTableStmt.executeUpdate();

                    // Update customer_orders with reserved_table
                    // Using UPSERT logic for customer_orders for consistency
                    PreparedStatement checkCustomerOrderStmt = conn.prepareStatement("SELECT COUNT(*) FROM customer_orders WHERE customer_name = ?");
                    checkCustomerOrderStmt.setString(1, customerName);
                    ResultSet customerOrderRs = checkCustomerOrderStmt.executeQuery();
                    customerOrderRs.next();
                    int customerOrderCount = customerOrderRs.getInt(1);

                    if (customerOrderCount > 0) {
                        PreparedStatement updateCustomerTableStmt = conn.prepareStatement("UPDATE customer_orders SET reserved_table = ? WHERE customer_name = ?");
                        updateCustomerTableStmt.setInt(1, table);
                        updateCustomerTableStmt.setString(2, customerName);
                        updateCustomerTableStmt.executeUpdate();
                    } else {
                        // If no prior order exists, insert a minimal entry
                        PreparedStatement insertCustomerTableStmt = conn.prepareStatement("INSERT INTO customer_orders (customer_name, reserved_table, is_paid, bill_amount) VALUES (?, ?, ?, ?)");
                        insertCustomerTableStmt.setString(1, customerName);
                        insertCustomerTableStmt.setInt(2, table);
                        insertCustomerTableStmt.setInt(3, 0);
                        insertCustomerTableStmt.setInt(4, 0);
                        insertCustomerTableStmt.executeUpdate();
                    }

                    new Alert(Alert.AlertType.INFORMATION, "Table " + table + " reserved for " + customerName).show();
                    stage.close();
                } else {
                    new Alert(Alert.AlertType.ERROR, "Table already reserved.").show();
                }
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Error reserving table: " + ex.getMessage()).show();
            }
        });

        box.getChildren().addAll(customerLabel, tableField, reserveBtn);
        stage.setScene(new Scene(box, 300, 200));
        stage.setTitle("Table Reservation");
        stage.show();
    }

    // Modified generateBill to accept customer name
    private void generateBill(String customerName) {
        Stage stage = new Stage();
        VBox box = new VBox(10);
        box.setStyle("-fx-padding: 10;");

        Label customerLabel = new Label("Generating bill for: " + customerName);
        customerLabel.setStyle("-fx-font-weight: bold;");

        Button generateBtn = new Button("Generate Bill");

        generateBtn.setOnAction(e -> {
            ArrayList<String> orders = cus_list.get(customerName);
            if (orders == null) {
                // Attempt to load from DB if not in memory
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("SELECT order_details, reserved_table, is_paid FROM customer_orders WHERE customer_name = ?")) {
                    pstmt.setString(1, customerName);
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        String orderDetails = rs.getString("order_details");
                        if (orderDetails != null && !orderDetails.isEmpty()) {
                            orders = new ArrayList<>(Arrays.asList(orderDetails.split(",")));
                            // Trim and normalize each item
                            orders.replaceAll(String::trim);
                            orders.replaceAll(String::toLowerCase);
                        } else {
                            orders = new ArrayList<>(); // No order details
                        }
                        cus_list.put(customerName, orders); // Add to in-memory for future use
                        if (rs.getInt("is_paid") == 1) {
                            paidCustomers.add(customerName);
                        }
                        String reservedTable = rs.getString("reserved_table");
                        if (reservedTable != null) {
                            cu_tab.put(customerName, reservedTable);
                        }
                    } else {
                        new Alert(Alert.AlertType.ERROR, "Customer not found in database or memory with an order.").show();
                        return;
                    }
                } catch (SQLException ex) {
                    new Alert(Alert.AlertType.ERROR, "Error fetching customer data: " + ex.getMessage()).show();
                    return;
                }
            }

            if (orders.isEmpty()) {
                new Alert(Alert.AlertType.INFORMATION, "No order found for " + customerName + ". Cannot generate bill.").show();
                stage.close();
                return;
            }

            int total = 0;
            StringBuilder invalidItems = new StringBuilder();

            for (String item : orders) {
                String price = menu.get(item);
                if (price != null) {
                    try {
                        total += Integer.parseInt(price);
                    } catch (NumberFormatException nfe) {
                        invalidItems.append(item).append(" (invalid price), ");
                    }
                } else {
                    invalidItems.append(item).append(" (not in menu), ");
                }
            }

            // Update in-memory
            cu_bill.put(customerName, total);
            paidCustomers.add(customerName);

            // Update database: mark as paid and update bill amount
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement pstmt = conn.prepareStatement("UPDATE customer_orders SET bill_amount = ?, is_paid = ? WHERE customer_name = ?")) {
                pstmt.setInt(1, total);
                pstmt.setInt(2, 1); // 1 for paid
                pstmt.setString(3, customerName);
                pstmt.executeUpdate();
            } catch (SQLException ex) {
                new Alert(Alert.AlertType.ERROR, "Error updating bill in database: " + ex.getMessage()).show();
            }

            // Release table in-memory and in DB
            if (cu_tab.containsKey(customerName)) {
                int table = Integer.parseInt(cu_tab.get(customerName));
                tableAvailability.put(table, true);
                cu_tab.remove(customerName); // Remove table association from in-memory
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE table_status SET is_available = 1 WHERE table_number = ?")) {
                    pstmt.setInt(1, table);
                    pstmt.executeUpdate();
                } catch (SQLException ex) {
                    System.err.println("Error freeing table in database: " + ex.getMessage());
                }
            }

            String result = "Name: " + customerName +
                    "\nOrder: " + String.join(", ", orders) +
                    "\nBill: Rs." + total +
                    "\nPoints Earned: " + (total / 10);

            if (invalidItems.length() > 0) {
                result += "\nInvalid or unrecognized items: " + invalidItems.substring(0, invalidItems.length() - 2) + "."; // Remove trailing ", "
            }

            new Alert(Alert.AlertType.INFORMATION, result).show();
            stage.close();
        });

        box.getChildren().addAll(customerLabel, generateBtn);
        stage.setScene(new Scene(box, 300, 200));
        stage.setTitle("Bill Generator");
        stage.show();
    }

    private void updateMenuPrice() {
        Stage stage = new Stage();
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 10;");

        TextField itemField = new TextField();
        itemField.setPromptText("Enter item name");

        TextField priceField = new TextField();
        priceField.setPromptText("Enter new price");

        Button update = new Button("Update Price");

        update.setOnAction(e -> {
            String item = itemField.getText().trim().toLowerCase();
            String newPrice = priceField.getText().trim();
            if (item.isEmpty() || newPrice.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Item name and new price cannot be empty.").show();
                return;
            }

            if (!newPrice.matches("\\d+")) { // Basic validation for price being a number
                new Alert(Alert.AlertType.WARNING, "Price must be a number.").show();
                return;
            }

            if (menu.containsKey(item)) {
                menu.put(item, newPrice); // Update in-memory
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("UPDATE menu_items SET price = ? WHERE item_name = ?")) {
                    pstmt.setString(1, newPrice);
                    pstmt.setString(2, item);
                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        new Alert(Alert.AlertType.INFORMATION, "Updated " + item + " price to Rs." + newPrice + " in database.").show();
                        stage.close();
                    } else {
                        new Alert(Alert.AlertType.ERROR, "Item not found in database to update.").show();
                    }
                } catch (SQLException ex) {
                    new Alert(Alert.AlertType.ERROR, "Error updating menu price in database: " + ex.getMessage()).show();
                }
            } else {
                new Alert(Alert.AlertType.ERROR, "Item not found in menu").show();
            }
        });

        layout.getChildren().addAll(itemField, priceField, update);
        stage.setScene(new Scene(layout, 300, 200));
        stage.setTitle("Update Price");
        stage.show();
    }

    private void addNewMenuItem() {
        Stage stage = new Stage();
        VBox layout = new VBox(10);
        layout.setStyle("-fx-padding: 10;");

        TextField itemField = new TextField();
        itemField.setPromptText("Enter new item name");

        TextField priceField = new TextField();
        priceField.setPromptText("Enter price");

        Button add = new Button("Add Item");

        add.setOnAction(e -> {
            String item = itemField.getText().trim().toLowerCase();
            String price = priceField.getText().trim();

            if (item.isEmpty() || price.isEmpty()) {
                new Alert(Alert.AlertType.WARNING, "Item name and price cannot be empty.").show();
                return;
            }

            if (!price.matches("\\d+")) { // Basic validation for price being a number
                new Alert(Alert.AlertType.WARNING, "Price must be a number.").show();
                return;
            }

            if (!menu.containsKey(item)) {
                menu.put(item, price); // Add to in-memory
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                     PreparedStatement pstmt = conn.prepareStatement("INSERT INTO menu_items (item_name, price) VALUES (?, ?)")) {
                    pstmt.setString(1, item);
                    pstmt.setString(2, price);
                    pstmt.executeUpdate();
                    new Alert(Alert.AlertType.INFORMATION, "Added " + item + " to menu at Rs." + price + " and saved to database.").show();
                    stage.close();
                } catch (SQLException ex) {
                    new Alert(Alert.AlertType.ERROR, "Error adding new menu item to database: " + ex.getMessage()).show();
                }
            } else {
                new Alert(Alert.AlertType.WARNING, "Item already exists. Use update instead.").show();
            }
        });

        layout.getChildren().addAll(itemField, priceField, add);
        stage.setScene(new Scene(layout, 300, 200));
        stage.setTitle("Add Item");
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
