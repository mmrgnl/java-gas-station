import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class Car {
    String fuelType;
    double money;
    double litersNeeded;

    int id;
    static int nextId = 1;

    Car(String fuelType, double money, double litersNeeded) {
        this.fuelType = fuelType;
        this.money = money;
        this.litersNeeded = litersNeeded;
        this.id = nextId++;
    }
}

class CarQueue {
    Queue<Car> carQueue;
    String fuelType;
    List<GasPump> pumps;

    boolean isQueuesFree() {

        for (GasPump pump : pumps) {

            if (!pump.isFree) {
                return false;
            }

        }

        return true;

    }

    CarQueue(String fuelType) {
        this.fuelType = fuelType;
    }

    void addCarToQueue(Car car) {
        int minimumLength = Integer.MAX_VALUE;
        GasPump best_pump = null;
        for (GasPump pump : pumps) {
            int isNotFree = pump.isFree ? 0 : 1;
            if ((pump.carQueue.size() + isNotFree) < minimumLength) {
                minimumLength = pump.carQueue.size() + isNotFree;
                best_pump = pump;
            }
        }
        if (best_pump == null)
            System.out.println("Ошибка при добавлении автомобиля в очередь");
        else
            best_pump.carQueue.add(car);
    }

}

class GasPump {
    String fuelType;
    Queue<Car> carQueue;
    double pricePerLiter;
    double litersAvailable;
    boolean isFree;
    Thread pumpThread;

    GasPump(String fuelType, double pricePerLiter, double litersAvailable) {
        this.fuelType = fuelType;
        this.pricePerLiter = pricePerLiter;
        this.litersAvailable = litersAvailable;
        this.isFree = true;
        this.carQueue = new LinkedList<>();
    }

    public synchronized void decrementI() {

        GasStation.lock.lock();
        try {
            GasStation.i--;

            if (GasStation.i == 0) {

                // &&(CarQueuear.isQueuesFree())
                System.out.printf("Время выполнения %d сек.%n",
                        (TimeUnit.SECONDS.convert(System.nanoTime() - GasStation.startTime, TimeUnit.NANOSECONDS)));
            }
        } finally {
            GasStation.lock.unlock();
        }
        System.out.println("De I " + GasStation.i);

    }

    boolean check(Car car) {

        double cost = car.litersNeeded * pricePerLiter;
        if (this.litersAvailable < car.litersNeeded) {
            System.out.printf("На станции недостаточно топлива %s  для Car %s. Имеется %s литров. %n",
                    car.fuelType, car.id, this.litersAvailable);
            return false;
        } else if (car.money >= cost) {
            double change = car.money - cost;
            if (!car.fuelType.equals("накачка"))
                System.out.printf("Автомобиль %d оплатил заправку, сдача = %.2f руб%n", car.id, change);
            System.out.printf("Автомобиль %d начал заправку %n", car.id, Thread.currentThread().getId());
            // (№потока: %d)
            return true;
        } else {
            System.out.printf("У автомобиля %d не хватает денег на заправку.%n", car.id);
            return false;
        }
    }

    static int a = 0;

    void startRefueling() {
        while (true) {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            synchronized (this) {
                if (isFree && !carQueue.isEmpty()) {
                    Car car = carQueue.poll();
                    if (check(car)) {
                        a++;
                        // System.out.println(a);
                        a--;
                        isFree = false;
                        try {
                            this.litersAvailable -= car.litersNeeded;
                            TimeUnit.MILLISECONDS.sleep((int) (car.litersNeeded * 500));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        if (car.fuelType.equals("накачка")) {
                            System.out.printf("Колеса автомобиля %d успешно накачаны %n", car.id);
                        } else {

                            System.out.printf("Автомобиль %d успешно заправлен%n", car.id);
                            decrementI();

                        }
                        isFree = true;
                    }
                }
            }
        }
    }

}

class GasStation {

    static long startTime;
    static long itsTime;
    static volatile int i;
    static Lock lock = new ReentrantLock();
    private final Map<String, List<GasPump>> pumps;
    private final Map<String, CarQueue> carQueues;

    public Map<String, List<GasPump>> get_pumps() {
        return pumps;
    }

    public synchronized void incrementI() {

        System.out.println("In I " + GasStation.i);

        GasStation.lock.lock();
        try {
            GasStation.i++;
        } finally {
            GasStation.lock.unlock();
        }
    }

    GasStation() {
        this.pumps = new HashMap<>();
        this.carQueues = new HashMap<>();
        // add_pump("92", 50.56, 1000000000);

        for (List<GasPump> pump_list : pumps.values()) { // паралельная работа бензоколонки
            for (GasPump pump : pump_list) {
                pump.pumpThread = new Thread(pump::startRefueling);
                pump.pumpThread.start();
            }
        }
    }

    void add_pump(String fuelType, double pricePerLiter, double litersAvailable) {
        GasPump pump = new GasPump(fuelType, pricePerLiter, litersAvailable);
        if (pumps.containsKey(fuelType))
            pumps.get(fuelType).add(pump);
        else
            pumps.put(fuelType, new ArrayList<>(List.of(pump)));

        if (!carQueues.containsKey(fuelType)) {
            carQueues.put(fuelType, new CarQueue(fuelType));
            carQueues.get(fuelType).pumps = new ArrayList<>(List.of(pump));
        } else
            carQueues.get(fuelType).pumps.add(pump);

        pump.pumpThread = new Thread(pump::startRefueling);
        pump.pumpThread.start();
    }

    void arrive(Car car) {

        if (pumps.containsKey(car.fuelType)) {
            if (car.fuelType.equals("накачка")) {
                System.out.printf("Автомобиль %d прибывает на станцию за воздухом.%n", car.id);
            } else {
                System.out.printf("Автомобиль %d прибывает на станцию за %s топливом.%n", car.id, car.fuelType);

                itsTime = (int) System.nanoTime();
                if (i == 0) {
                    GasStation.startTime = (long) System.nanoTime();
                    System.out.println("Setting start time: " + GasStation.startTime + "  " + (long) System.nanoTime());
                }

                incrementI();
                carQueues.get(car.fuelType).addCarToQueue(car);
            }
        } else {
            System.out.printf("Тип топлива %s недоступен на этой станции.%n", car.fuelType);
        }
    }

}

public class main {

    public static void main(String[] args) {

        GasStation station = new GasStation();
        oneThread(station);
        // multiThread(station);

        Map<String, List<GasPump>> pumps = station.get_pumps();
        // Ваш существующий код обработки строки
        System.out.println("Заправочная станция");
        for (List<GasPump> pump_list : pumps.values()) { // паралельная работа бензоколонки
            for (GasPump pump : pump_list) {
                if (pump.fuelType.equals("накачка"))
                    System.out.println("Накачка шин бесплатно в любых объемах");
                else
                    System.out.println("Тип топлива: " + pump.fuelType + ";  цена за Литр: " + pump.pricePerLiter
                            + " руб" + ";  Имеется литров на станции: " + pump.litersAvailable);
            }
        }

        // try (BufferedReader reader = new BufferedReader(new
        // FileReader("/Users/pisarikmaksim/IdeaProjects/GasStation/cars"))) {
        // String line;
        // while ((line = reader.readLine()) != null) {
        // processInputLine(line, station);
        // }
        // } catch (IOException e) {
        // e.printStackTrace();
        // }

        // Принятие ввода с консоли
        // Scanner consoleScanner = new Scanner(System.in);
        // System.out.println("Введите данные (для завершения введите 'exit'):");

        // while (true) {
        // String inputLine = consoleScanner.nextLine();
        // if (inputLine.equals("exit")) {
        // break;
        // }
        // processInputLine(inputLine, station);

        // }
    }

    private static void oneThread(GasStation station) {

        station.add_pump("92", 10, 100000000);

        for (int i = 0; i <= 10; i++) {
            station.arrive(new Car("92", 100000, 10));
        }

    }

    private static void multiThread(GasStation station) {

        for (int i = 0; i <= 10; i++) {
            station.add_pump("92", 10, 100000000);
        }

        for (int i = 0; i <= 10; i++) {
            station.arrive(new Car("92", 100000, 10));
        }

    }

    private static void fall(GasStation station) {

        for (int i = 0; i <= 100000; i++) {
            station.add_pump("92", 10, 100000000);
        }

        for (int i = 0; i <= 100000; i++) {
            station.arrive(new Car("92", 100000, 100));
        }

    }

    private static void processInputLine(String inputLine, GasStation station) {

        Scanner scanner = new Scanner(System.in);

        Map<String, List<GasPump>> pumps = station.get_pumps();

        String[] parts = inputLine.split("\\s+");
        if (parts.length == 3 || (parts.length == 2 && parts[0].equals("накачка"))) {
            String fuelType = parts[0];
            String moneyStr = parts[1];
            String litersStr = "";
            if (parts.length == 3)
                litersStr = parts[2];
            if (parts.length == 2) {
                litersStr = parts[1];
                moneyStr = "1";
            }
            if (!fuelType.matches("95|92|100|DF|накачка") || !moneyStr.matches("^\\d+(\\.\\d{1,2})?$")
                    || !litersStr.matches("^\\d+(\\.\\d)?$")) {
                System.out.println("Неправильный формат ввода данных.");
            } else {
                double money = Double.parseDouble(moneyStr);
                if (fuelType.equals("накачака"))
                    money = 1;
                double litersNeeded = Double.parseDouble(litersStr);
                if (money > 0 && litersNeeded > 0) {
                    station.arrive(new Car(fuelType, money, litersNeeded));
                } else {
                    System.out.println("Необходимые деньги и литры должны быть положительными числами");
                }
            }
        } else if (parts.length == 1 && Objects.equals(parts[0], "fuel")) {
            pumps = station.get_pumps();
            for (List<GasPump> pump_list : pumps.values()) { // паралельная работа бензоколонки
                for (GasPump pump : pump_list) {
                    if (pump.fuelType.equals("накачка"))
                        System.out.println("Накачка шин бесплатно в любых объемах");
                    else
                        System.out.println("Тип топлива: " + pump.fuelType + ";  цена за Литр: " + pump.pricePerLiter
                                + " руб" + ";  Имеется литров на станции: " + pump.litersAvailable);
                }
            }

        } else if (parts.length == 4 && Objects.equals(parts[0], "add_pump")) {
            station.add_pump(parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } else {
            System.out.println(
                    "Неверные входные данные! Правильный вариант: - \"<тип топлива> <деньги> <необходимые литры>\"");
        }
    }
}

class ThreadInfo {

    public static void printThreadInfo() {
        // Получаем количество доступных процессоров
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("Количество доступных процессоров: " + processors);

        // Даем время для запуска потоков
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Получаем количество активных потоков
        int activeThreads = Thread.activeCount();
        System.out.println("Количество активных потоков: " + activeThreads);
    }
}

class ProcessorInfo {
    public static void printProcessorInfo() {
        // Получаем количество доступных процессоров
        int processors = Runtime.getRuntime().availableProcessors();
        System.out.println("Количество доступных процессоров: " + processors);
    }

}
