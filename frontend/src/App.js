import React, { useEffect, useState } from "react";
import axios from "axios";
import { Line } from "react-chartjs-2";
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
} from "chart.js";

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend
);

const api = axios.create({ baseURL: "http://localhost:8080/api" });

export default function Dashboard() {
  const [currentMode, setCurrentMode] = useState("TRAINING"); // Track current mode
  const [account, setAccount] = useState(null);
  const [trades, setTrades] = useState([]);
  const [portfolio, setPortfolio] = useState([]);
  const [prices, setPrices] = useState([]);
  const [botStatus, setBotStatus] = useState({});

  // Fetch data filtered by mode
  const fetchData = async (mode) => {
    try {
      const [accRes, tradesRes, portRes, priceRes, statusRes] = await Promise.all([
        api.get(`/account?mode=${mode}`),
        api.get(`/trades?mode=${mode}`),
        api.get(`/portfolio?mode=${mode}`),
        api.get(`/prices?mode=${mode}`),
        api.get("/bot/status"),
      ]);

      setAccount(accRes.data);
      setTrades(tradesRes.data);
      setPortfolio(portRes.data);
      setPrices(priceRes.data.reverse()); // chronological order
      setBotStatus(statusRes.data);
    } catch (err) {
      console.error("Error fetching data", err);
    }
  };

  // Auto-refresh every 5 seconds
  useEffect(() => {
    fetchData(currentMode);
    const interval = setInterval(() => fetchData(currentMode), 5000);
    return () => clearInterval(interval);
  }, [currentMode]);

  // Start bot in selected mode
  const handleStart = (mode) => {
    setCurrentMode(mode); // update current mode
    api.post(`/bot/start?mode=${mode}`).then(() => fetchData(mode));
  };

  const handleStop = () => api.post("/bot/stop").then(() => fetchData(currentMode));
  const handleReset = () => api.post("/bot/reset").then(() => fetchData(currentMode));

  const chartData = {
    labels: prices.map((p) => new Date(p.timestamp).toLocaleTimeString()),
    datasets: [
      {
        label: "BTC Price (USD)",
        data: prices.map((p) => p.price),
        borderColor: "#2563eb",
        backgroundColor: "rgba(37, 99, 235, 0.2)",
        tension: 0.3,
        fill: true,
      },
    ],
  };

  return (
    <div className="min-h-screen bg-gray-50 p-8 space-y-10 font-sans">
      <h1 className="text-3xl font-extrabold text-gray-800 tracking-tight">Trading Bot Dashboard</h1>

      {/* Controls */}
      <div className="flex flex-wrap gap-4">
        <button
          onClick={() => handleStart("TRAINING")}
          className={`px-5 py-2 font-medium rounded-lg shadow text-white ${
            currentMode === "TRAINING" ? "bg-green-700" : "bg-green-600 hover:bg-green-700"
          }`}
        >
          Start Training
        </button>
        <button
          onClick={() => handleStart("TRADING")}
          className={`px-5 py-2 font-medium rounded-lg shadow text-white ${
            currentMode === "TRADING" ? "bg-blue-700" : "bg-blue-600 hover:bg-blue-700"
          }`}
        >
          Start Trading
        </button>
        <button onClick={handleStop} className="px-5 py-2 bg-yellow-500 hover:bg-yellow-600 text-white font-medium rounded-lg shadow">
          Pause
        </button>
        <button onClick={handleReset} className="px-5 py-2 bg-red-600 hover:bg-red-700 text-white font-medium rounded-lg shadow">
          Reset
        </button>
      </div>

      {/* Bot Status */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-semibold text-gray-700 mb-2">Bot Status</h2>
        <p className="text-sm text-gray-600">
          Mode: <span className="font-medium text-gray-800">{botStatus.mode}</span>
        </p>
        <p className="text-sm text-gray-600">
          Running: <span className="font-medium text-gray-800">{botStatus.is_running ? "Yes" : "No"}</span>
        </p>
        <p className="text-sm text-gray-600">
          Last Run: <span className="font-medium text-gray-800">{botStatus.last_run}</span>
        </p>
      </div>

      {/* Account Overview */}
      {account && (
        <div className="bg-white p-6 rounded-xl shadow">
          <h2 className="text-xl font-semibold text-gray-700 mb-4">Account Overview ({currentMode})</h2>
          <div className="grid grid-cols-3 gap-4 text-gray-700">
            <div className="p-4 bg-gray-100 rounded-lg text-center">
              <p className="text-sm font-medium">Balance</p>
              <p className="text-lg font-bold">${account.balance}</p>
            </div>
            <div className="p-4 bg-gray-100 rounded-lg text-center">
              <p className="text-sm font-medium">Portfolio Value</p>
              <p className="text-lg font-bold">${account.portfolio_value}</p>
            </div>
            <div className="p-4 bg-gray-100 rounded-lg text-center">
              <p className="text-sm font-medium">Total Value</p>
              <p className="text-lg font-bold">${account.total_value}</p>
            </div>
          </div>
        </div>
      )}

      {/* Portfolio */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-semibold text-gray-700 mb-4">Portfolio ({currentMode})</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full border border-gray-200 text-sm">
            <thead className="bg-gray-100 text-gray-700">
              <tr>
                <th className="border px-3 py-2">Symbol</th>
                <th className="border px-3 py-2">Quantity</th>
                <th className="border px-3 py-2">Avg Buy Price</th>
                <th className="border px-3 py-2">Current Price</th>
                <th className="border px-3 py-2">Current Value</th>
                <th className="border px-3 py-2">Unrealized PnL</th>
              </tr>
            </thead>
            <tbody>
              {portfolio.map((p) => (
                <tr key={p.id} className="hover:bg-gray-50">
                  <td className="border px-3 py-2">{p.symbol}</td>
                  <td className="border px-3 py-2">{p.quantity}</td>
                  <td className="border px-3 py-2">{p.average_buy_price}</td>
                  <td className="border px-3 py-2">{p.current_price}</td>
                  <td className="border px-3 py-2">{p.current_value}</td>
                  <td className="border px-3 py-2">{p.unrealized_pnl}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Price Chart */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-semibold text-gray-700 mb-4">Price History ({currentMode})</h2>
        <Line data={chartData} />
      </div>

      {/* Trade History */}
      <div className="bg-white p-6 rounded-xl shadow">
        <h2 className="text-xl font-semibold text-gray-700 mb-4">Trade History ({currentMode})</h2>
        <div className="overflow-x-auto">
          <table className="min-w-full border border-gray-200 text-sm">
            <thead className="bg-gray-100 text-gray-700">
              <tr>
                <th className="border px-3 py-2">Date</th>
                <th className="border px-3 py-2">Action</th>
                <th className="border px-3 py-2">Quantity</th>
                <th className="border px-3 py-2">Price</th>
                <th className="border px-3 py-2">P/L</th>
              </tr>
            </thead>
            <tbody>
              {trades.map((t) => (
                <tr key={t.id} className="hover:bg-gray-50">
                  <td className="border px-3 py-2">{new Date(t.timestamp).toLocaleString()}</td>
                  <td className="border px-3 py-2">{t.trade_type}</td>
                  <td className="border px-3 py-2">{t.quantity}</td>
                  <td className="border px-3 py-2">{t.price}</td>
                  <td className="border px-3 py-2">{t.profit_loss}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
