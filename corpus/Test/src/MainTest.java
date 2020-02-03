import static org.junit.Assert.*;

public class MainTest {

    @org.junit.Test
    public void testAdd() {
        Main main = new Main();
        assertEquals(5, main.add(2,3) );
    }
}