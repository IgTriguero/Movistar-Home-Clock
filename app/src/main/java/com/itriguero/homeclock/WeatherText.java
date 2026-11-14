package com.itriguero.homeclock;

/**
 * Traducción de los códigos WMO de Open-Meteo a icono y texto.
 *
 * Es una extracción literal del antiguo {@code MainActivity.weatherInfo};
 * la UI se conectará a esta clase en una fase posterior.
 */
final class WeatherText {

    private WeatherText() {
    }

    static String icon(int code, int isDay) {
        switch (code) {
            case 0:
                return isDay == 1 ? "☀️" : "🌙";
            case 1:
                return isDay == 1 ? "🌤️" : "🌙";
            case 2:
                return "⛅";
            case 3:
                return "☁️";
            case 45:
            case 48:
                return "🌫️";
            case 51:
            case 53:
            case 55:
                return "🌦️";
            case 56:
            case 57:
                return "🌧️";
            case 61:
            case 63:
            case 65:
                return "🌧️";
            case 66:
            case 67:
                return "🌧️";
            case 71:
            case 73:
            case 75:
                return "🌨️";
            case 77:
                return "🌨️";
            case 80:
            case 81:
            case 82:
                return "🌦️";
            case 85:
            case 86:
                return "🌨️";
            case 95:
                return "⛈️";
            case 96:
            case 99:
                return "⛈️";
            default:
                return "🌡️";
        }
    }

    static String description(int code) {
        switch (code) {
            case 0:
                return "Despejado";
            case 1:
                return "Mayormente despejado";
            case 2:
                return "Parcialmente nublado";
            case 3:
                return "Nublado";
            case 45:
            case 48:
                return "Niebla";
            case 51:
            case 53:
            case 55:
                return "Llovizna";
            case 56:
            case 57:
                return "Llovizna helada";
            case 61:
            case 63:
            case 65:
                return "Lluvia";
            case 66:
            case 67:
                return "Lluvia helada";
            case 71:
            case 73:
            case 75:
                return "Nieve";
            case 77:
                return "Granos de nieve";
            case 80:
            case 81:
            case 82:
                return "Chubascos";
            case 85:
            case 86:
                return "Chubascos de nieve";
            case 95:
                return "Tormenta";
            case 96:
            case 99:
                return "Tormenta con granizo";
            default:
                return "Tiempo variable";
        }
    }
}
