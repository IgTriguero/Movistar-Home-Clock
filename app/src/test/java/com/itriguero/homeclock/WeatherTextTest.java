package com.itriguero.homeclock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WeatherTextTest {

    @Test
    public void clearSkyDependsOnDayAndNight() {
        assertEquals("☀️", WeatherText.icon(0, 1));
        assertEquals("🌙", WeatherText.icon(0, 0));
        assertEquals("Despejado", WeatherText.description(0));
    }

    @Test
    public void mostlyClearHasNightIcon() {
        assertEquals("🌤️", WeatherText.icon(1, 1));
        assertEquals("🌙", WeatherText.icon(1, 0));
        assertEquals("Mayormente despejado", WeatherText.description(1));
    }

    @Test
    public void partiallyCloudy() {
        assertEquals("⛅", WeatherText.icon(2, 1));
        assertEquals("Parcialmente nublado", WeatherText.description(2));
    }

    @Test
    public void cloudy() {
        assertEquals("☁️", WeatherText.icon(3, 1));
        assertEquals("Nublado", WeatherText.description(3));
    }

    @Test
    public void fog() {
        assertEquals("🌫️", WeatherText.icon(45, 1));
        assertEquals("Niebla", WeatherText.description(45));
        assertEquals("Niebla", WeatherText.description(48));
    }

    @Test
    public void rain() {
        assertEquals("🌧️", WeatherText.icon(61, 1));
        assertEquals("Lluvia", WeatherText.description(61));
        assertEquals("Lluvia", WeatherText.description(65));
    }

    @Test
    public void snow() {
        assertEquals("🌨️", WeatherText.icon(71, 1));
        assertEquals("Nieve", WeatherText.description(71));
        assertEquals("Nieve", WeatherText.description(75));
    }

    @Test
    public void thunderstorm() {
        assertEquals("⛈️", WeatherText.icon(95, 1));
        assertEquals("Tormenta", WeatherText.description(95));
    }

    @Test
    public void thunderstormWithHail() {
        assertEquals("⛈️", WeatherText.icon(99, 1));
        assertEquals("Tormenta con granizo", WeatherText.description(99));
        assertEquals("Tormenta con granizo", WeatherText.description(96));
    }

    @Test
    public void unknownCodeFallsBack() {
        assertEquals("🌡️", WeatherText.icon(42, 1));
        assertEquals("Tiempo variable", WeatherText.description(42));
    }
}
