package spy.test;

public class Main {

    public static void main(String args[]) {
        Stack<Sup> s = new Stack<Sup>();
        s.push(new Sub(1));
        s.push(new Sup(2));
        s.pop();
        m1();
        m5();
        s.pop();
        theEnd();
    }

    public static int m1() {
        try {
            m2();
            m4();
            return 1;
        } catch(Exception e) {
            return 0;
        }
    }

    public static void m2() {
        m3();
    }

    public static void m3() {
        throw new RuntimeException("thrrowwww!");
    }

    public static void m4() {
        //throw new RuntimeException("thrrowwww!");
    }

    public static void m5() {
    }

    public static void theEnd() {
    }

}

class Sup {
    private int val = -1;
        
    Sup(int val) {
        this.val = val;
    }

    public String toString() {
        return "val: " + val;
    }
}

class Sub extends Sup {
    Sub(int val) {
        super(val + 1);
    }
}

class Stack<T extends Sup> {
    
    Object[] items = new Object[10];
    int top = -1;

    public void push(T item) {
        items[++top] = item;
    }

    public T pop() {
        return (T) items[top--];
    }
}