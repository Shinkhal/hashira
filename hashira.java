import org.json.JSONObject;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A record to represent a 2D point with BigInteger coordinates.
 */
record Point(BigInteger x, BigInteger y) {}

/**
 * A class to represent and perform arithmetic on rational numbers (fractions).
 */
class Rational {
    private final BigInteger num, den; // Numerator, Denominator

    public Rational(BigInteger numerator) {
        this(numerator, BigInteger.ONE);
    }

    public Rational(BigInteger numerator, BigInteger denominator) {
        if (denominator.equals(BigInteger.ZERO)) {
            throw new IllegalArgumentException("Denominator cannot be zero.");
        }
        BigInteger gcd = numerator.gcd(denominator);
        BigInteger tempNum = numerator.divide(gcd);
        BigInteger tempDen = denominator.divide(gcd);

        // Normalization logic to ensure the denominator is always positive
        if (tempDen.signum() < 0) {
            this.num = tempNum.negate();
            this.den = tempDen.negate();
        } else {
            this.num = tempNum;
            this.den = tempDen;
        }
    }

    public Rational add(Rational other) {
        BigInteger newNum = this.num.multiply(other.den).add(other.num.multiply(this.den));
        BigInteger newDen = this.den.multiply(other.den);
        return new Rational(newNum, newDen);
    }

    public Rational multiply(Rational other) {
        BigInteger newNum = this.num.multiply(other.num);
        BigInteger newDen = this.den.multiply(other.den);
        return new Rational(newNum, newDen);
    }

    public BigInteger getNumerator() {
        if (!this.den.equals(BigInteger.ONE)) {
            throw new IllegalStateException("Result is not an integer. Denominator was " + this.den);
        }
        return this.num;
    }
}

/**
 * Main class to solve the Secret Sharing problem robustly.
 */
public class hashira {

    /**
     * Calculates the polynomial's value at x=0 using Lagrange Interpolation for a given set of points.
     */
    public static BigInteger lagrangeAtZero(List<Point> points) {
        Rational secret = new Rational(BigInteger.ZERO);
        for (int j = 0; j < points.size(); j++) {
            Rational lagrangeTerm = new Rational(points.get(j).y());
            for (int i = 0; i < points.size(); i++) {
                if (i == j) continue;
                BigInteger num = points.get(i).x().negate();
                BigInteger den = points.get(j).x().subtract(points.get(i).x());
                lagrangeTerm = lagrangeTerm.multiply(new Rational(num, den));
            }
            secret = secret.add(lagrangeTerm);
        }
        return secret.getNumerator();
    }

    /**
     * Helper to generate all combinations of k elements from a list.
     */
    private static void generateCombinations(List<Point> points, int k, int start, List<Point> currentCombo, Consumer<List<Point>> onCombinationFound) {
        if (currentCombo.size() == k) {
            onCombinationFound.accept(new ArrayList<>(currentCombo));
            return;
        }
        for (int i = start; i < points.size(); i++) {
            currentCombo.add(points.get(i));
            generateCombinations(points, k, i + 1, currentCombo, onCombinationFound);
            currentCombo.remove(currentCombo.size() - 1); // Backtrack
        }
    }

    /**
     * Finds the most likely secret by checking all combinations of k shares.
     * This handles the edge case of having incorrect/corrupted shares.
     */
    public static BigInteger findMostLikelySecret(String jsonFilePath) throws IOException {
        String content = new String(Files.readAllBytes(Paths.get(jsonFilePath)));
        JSONObject json = new JSONObject(content);

        int k = json.getJSONObject("keys").getInt("k");
        List<Point> allPoints = new ArrayList<>();
        for (String key : json.keySet()) {
            if (key.equals("keys")) continue;
            BigInteger x = new BigInteger(key);
            JSONObject pointInfo = json.getJSONObject(key);
            int base = Integer.parseInt(pointInfo.getString("base"));
            BigInteger y = new BigInteger(pointInfo.getString("value"), base);
            allPoints.add(new Point(x, y));
        }

        if (allPoints.size() < k) {
            throw new IllegalArgumentException("Cannot solve: Not enough shares provided (n < k).");
        }

        // Count occurrences of each potential secret
        Map<BigInteger, Integer> secretCounts = new HashMap<>();
        generateCombinations(allPoints, k, 0, new ArrayList<>(), combo -> {
            try {
                // Attempt to calculate the secret for the current combination
                BigInteger potentialSecret = lagrangeAtZero(combo);
                secretCounts.put(potentialSecret, secretCounts.getOrDefault(potentialSecret, 0) + 1);
            } catch (IllegalStateException e) {
                // This combination resulted in a non-integer, meaning the shares are inconsistent.
                // We simply ignore this invalid combination and let the loop continue to the next one.
            }
        });

        // Find the entry with the highest count (the most common secret)
        Optional<Map.Entry<BigInteger, Integer>> maxEntry = secretCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue());

        // Extract the BigInteger key (the secret) from that entry and return it
        if (maxEntry.isPresent()) {
            return maxEntry.get().getKey();
        } else {
            throw new RuntimeException("Could not determine a secret. All share combinations were invalid.");
        }
    }

    /**
     * Helper method to solve for a single file and print the result.
     */
    private static void solveAndPrint(String filename) {
        try {
            System.out.println("Processing file: " + filename);
            BigInteger secret = findMostLikelySecret(filename);
            System.out.println("✅ The secret is: " + secret);
        } catch (IOException e) {
            System.err.println("Error: Could not read file '" + filename + "'. Make sure it's in the project root.");
        } catch (Exception e) {
            System.err.println("An unexpected error occurred for " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        System.out.println("--- 🔎 Starting Secret Reconstruction ---");

        solveAndPrint("testcase1.json");
        System.out.println(); // Add a blank line for separation
        solveAndPrint("testcase2.json");

        System.out.println("--- Finished ---");
    }
}
