package uk.co.real_logic;

import sun.misc.Unsafe;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Queue;

public class ManyToManyConcurrentArrayQueue<E> implements Queue<E>
{
    private static final Unsafe unsafe;
    private static final int arrayBase;
    private static final int arrayScale;

    static
    {
        try
        {
            unsafe = Util.getUnsafe();
            arrayBase = unsafe.arrayBaseOffset(Object[].class);
            arrayScale = Util.calculateShiftForScale(unsafe.arrayIndexScale(Object[].class));
        }
        catch (final Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }

    private final E[] buffer;
    private final int mask;
    private final int capacity;

    private final AtomicCounter head = new AtomicCounter(0);
    private final AtomicCounter tail = new AtomicCounter(0);

    private final AtomicCounter tailCache = new AtomicCounter(0);
    private final AtomicCounter headCache = new AtomicCounter(0);

    @SuppressWarnings("unchecked")
    public ManyToManyConcurrentArrayQueue(final int capacity)
    {
        final int actualCapacity = Util.findNextPositivePowerOfTwo(capacity);
        mask = actualCapacity - 1;
        this.capacity = actualCapacity;
        buffer = (E[])new Object[actualCapacity];
    }

    public boolean add(final E e)
    {
        if (offer(e))
        {
            return true;
        }

        throw new IllegalStateException("Queue is full");
    }

    public boolean offer(final E e)
    {
        // TODO

        return true;
    }

    @SuppressWarnings("unchecked")
    public E poll()
    {
        // TODO
        return null;
    }

    public E remove()
    {
        final E e = poll();
        if (null == e)
        {
            throw new IllegalStateException("Queue is empty");
        }

        return e;
    }

    public E element()
    {
        final E e = peek();
        if (null == e)
        {
            throw new NoSuchElementException("Queue is empty");
        }

        return e;
    }

    public E peek()
    {
        final int index = (int)head.get() & mask;
        return getElementVolatile(index);
    }

    public int size()
    {
        int size;
        do
        {
            final long currentHead = head.get();
            final long currentTail = tail.get();
            size = (int)(currentTail - currentHead);
        }
        while (size > capacity);

        return size;
    }

    public boolean isEmpty()
    {
        return tail.get() == head.get();
    }

    public boolean contains(final Object o)
    {
        if (null == o)
        {
            return false;
        }

        for (long i = head.get(), limit = tail.get(); i < limit; i++)
        {
            final E e = getElementVolatile((int)i & mask);
            if (o.equals(e))
            {
                return true;
            }
        }

        return false;
    }

    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    public Object[] toArray()
    {
        throw new UnsupportedOperationException();
    }

    public <T> T[] toArray(final T[] a)
    {
        throw new UnsupportedOperationException();
    }

    public boolean remove(final Object o)
    {
        throw new UnsupportedOperationException();
    }

    public boolean containsAll(final Collection<?> c)
    {
        for (final Object o : c)
        {
            if (!contains(o))
            {
                return false;
            }
        }

        return true;
    }

    public boolean addAll(final Collection<? extends E> c)
    {
        for (final E o : c)
        {
            add(o);
        }

        return true;
    }

    public boolean removeAll(final Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    public boolean retainAll(final Collection<?> c)
    {
        throw new UnsupportedOperationException();
    }

    public void clear()
    {
        throw new UnsupportedOperationException();
    }

    private static long calculateOffset(final int index)
    {
        return arrayBase + ((long)index << arrayScale);
    }

    @SuppressWarnings("unchecked")
    private E getElementVolatile(final int index)
    {
        return (E)unsafe.getObjectVolatile(buffer, calculateOffset(index));
    }
}
