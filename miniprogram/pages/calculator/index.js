const KEYS = [
  { id: "r1", keys: ["C", "⌫", "%", "÷"] },
  { id: "r2", keys: ["7", "8", "9", "×"] },
  { id: "r3", keys: ["4", "5", "6", "−"] },
  { id: "r4", keys: ["1", "2", "3", "+"] },
  { id: "r5", keys: ["±", "0", ".", "="] }
];

const OPS = { "÷": "/", "×": "*", "−": "-", "+": "+" };

function calc(a, op, b) {
  if (op === "/") {
    if (b === 0) return null;
    return a / b;
  }
  if (op === "*") return a * b;
  if (op === "-") return a - b;
  return a + b;
}

function display(value) {
  if (value === null || isNaN(value) || !isFinite(value)) return "错误";
  // 消除浮点尾差并限制长度
  let text = String(Math.round(value * 1e10) / 1e10);
  if (text.length > 12) {
    text = Number(value).toExponential(6);
  }
  return text;
}

Page({
  data: {
    keys: KEYS,
    screen: "0",
    expression: ""
  },

  // 立即执行式计算器状态
  _acc: null,
  _op: null,
  _entering: true,

  onKeyTap(event) {
    const key = event.currentTarget.dataset.key;
    if (key >= "0" && key <= "9") {
      this.inputDigit(key);
    } else if (key === ".") {
      this.inputDot();
    } else if (key === "C") {
      this.clearAll();
    } else if (key === "⌫") {
      this.backspace();
    } else if (key === "±") {
      this.negate();
    } else if (key === "%") {
      this.percent();
    } else if (key === "=") {
      this.equals();
    } else if (OPS[key]) {
      this.operator(OPS[key], key);
    }
  },

  inputDigit(d) {
    let screen = this.data.screen;
    if (!this._entering) {
      screen = "0";
      this._entering = true;
    }
    screen = screen === "0" ? d : screen + d;
    if (screen.replace(/[-.]/g, "").length > 12) return;
    this.setData({ screen });
  },

  inputDot() {
    let screen = this.data.screen;
    if (!this._entering) {
      screen = "0";
      this._entering = true;
    }
    if (screen.indexOf(".") >= 0) return;
    this.setData({ screen: screen + "." });
  },

  clearAll() {
    this._acc = null;
    this._op = null;
    this._entering = true;
    this.setData({ screen: "0", expression: "" });
  },

  backspace() {
    if (!this._entering) return;
    let screen = this.data.screen.slice(0, -1);
    if (!screen || screen === "-") screen = "0";
    this.setData({ screen });
  },

  negate() {
    const screen = this.data.screen;
    if (screen === "0" || screen === "错误") return;
    this.setData({
      screen: screen.startsWith("-") ? screen.slice(1) : "-" + screen
    });
  },

  percent() {
    const value = parseFloat(this.data.screen);
    if (isNaN(value)) return;
    this.setData({ screen: display(value / 100) });
    this._entering = false;
  },

  operator(op, symbol) {
    const current = parseFloat(this.data.screen);
    if (isNaN(current)) return;
    if (this._op !== null && this._entering) {
      const result = calc(this._acc, this._op, current);
      this._acc = result;
      this.setData({ screen: display(result) });
    } else if (this._acc === null || this._entering) {
      this._acc = current;
    }
    this._op = op;
    this._entering = false;
    this.setData({ expression: display(this._acc) + " " + symbol });
  },

  equals() {
    const current = parseFloat(this.data.screen);
    if (this._op === null || isNaN(current)) return;
    const result = calc(this._acc, this._op, current);
    this._acc = null;
    this._op = null;
    this._entering = false;
    this.setData({ screen: display(result), expression: "" });
  }
});
