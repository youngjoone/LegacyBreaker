//4차수정: 신규 파일 전체 — 변수 선언 또는 이름을 선택해 Explore Variable Relations 실행.
class VariableExample {
    int field = 1;
    int[] values = {1, 2};

    void run(int input) {
        int amount = input;
        amount = 2;
        (amount) = 4;
        amount += input;
        amount++;
        --amount;
        if (amount > 0) consume(amount);
        int copy = amount;
        this.field = amount;
        consume(this.field);
        values[0] = amount;
        values[0]++;
        values = new int[]{2};
        input = 3;
        for (int item : values) consume(item);
        boolean flag = true;
        if (!flag) consume(1);
        flag = false;
        int captured = 1;
        Runnable task = () -> consume(captured);
        Runnable mutate = () -> { int inner = 1; inner = 2; consume(inner); };
    }

    void shadow(int field) {
        int amount = 9;
        consume(amount);
        consume(field);
    }
    void consume(int value) { }
}
