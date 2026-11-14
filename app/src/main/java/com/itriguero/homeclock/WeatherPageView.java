package com.itriguero.homeclock;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Página de tiempo de una ciudad para {@link WeatherPager}.
 *
 * <p>Infla {@code view_weather_page.xml} y solo pinta datos ya disponibles; no
 * consulta red ni caché. La accesibilidad la aporta el paginador, por lo que
 * todo el contenido de la página queda oculto como descendiente accesible.
 */
public class WeatherPageView extends LinearLayout {

    private final TextView icon;
    private final TextView temperature;
    private final TextView city;
    private final TextView description;
    private final TextView details;

    public WeatherPageView(Context context) {
        this(context, null);
    }

    public WeatherPageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public WeatherPageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setOrientation(VERTICAL);
        setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

        LayoutInflater.from(context).inflate(R.layout.view_weather_page, this, true);

        icon = findViewById(R.id.weatherIcon);
        temperature = findViewById(R.id.weatherTemperature);
        city = findViewById(R.id.weatherCity);
        description = findViewById(R.id.weatherDescription);
        details = findViewById(R.id.weatherDetails);
    }

    /**
     * Pinta el tiempo de una ciudad. Si {@code weather} es nulo se comporta
     * como {@link #bindEmpty(String)}, para que la UI nunca muestre datos de
     * otra ciudad.
     */
    public void bind(String cityName, Weather weather) {
        if (weather == null) {
            bindEmpty(cityName);
            return;
        }

        icon.setText(WeatherText.icon(weather.weatherCode, weather.isDay));
        temperature.setText(String.format(Locale.getDefault(), "%.0f°", weather.temp));
        city.setText(cityName == null ? "" : cityName);
        description.setText(WeatherText.description(weather.weatherCode));
        details.setText(getContext().getString(R.string.weather_page_details,
                weather.feels, weather.humidity, weather.wind));
    }

    /** Placeholder para una ciudad sin datos disponibles. */
    public void bindEmpty(String cityName) {
        icon.setText(R.string.weather_icon_placeholder);
        temperature.setText(R.string.temperature_placeholder);
        city.setText(cityName == null ? "" : cityName);
        description.setText(R.string.weather_empty);
        details.setText("");
    }
}
